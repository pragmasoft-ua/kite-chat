/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SsmConfigSourceTest {

  @Mock private SsmParameterConfiguration configuration;
  @Mock private AWSSimpleSystemsManagementAsync client;
  @Captor private ArgumentCaptor<GetParametersRequest> requestCaptor;

  @Test
  void should_retrieve_parameters() {
    String token = "telegram.token";
    String secret = "dev-telegram.secret";
    Parameter tokenParam = mock(Parameter.class);
    Parameter secretParam = mock(Parameter.class);
    GetParametersResult parametersResult = mock(GetParametersResult.class);
    String tokenValue = "token-value";
    String secretValue = "secret-value";
    Set<String> expectedParameters = Set.of("telegram.token", "telegram.secret");
    Set<String> availableParams = new HashSet<>();
    Set<String> dependentParams = new HashSet<>();
    availableParams.add(token);
    dependentParams.add(secret);

    doReturn("dev").when(configuration).getEnv();
    doReturn("-").when(configuration).getEnvDelimiter();
    doReturn(availableParams).when(configuration).getAvailableParameters();
    doReturn(dependentParams).when(configuration).getDependentParameters();
    doReturn(parametersResult).when(client).getParameters(any());

    doReturn(List.of(tokenParam, secretParam)).when(parametersResult).getParameters();
    doReturn(token).when(tokenParam).getName();
    doReturn(tokenValue).when(tokenParam).getValue();
    doReturn(secret).when(secretParam).getName();
    doReturn(secretValue).when(secretParam).getValue();

    SsmConfigSource configSource = SsmConfigSource.create(configuration, client);

    verify(client, times(1)).getParameters(requestCaptor.capture());
    Assertions.assertEquals(List.of(secret, token), requestCaptor.getValue().getNames());
    Assertions.assertEquals(expectedParameters, configSource.getPropertyNames());
    Assertions.assertEquals(tokenValue, configSource.getValue(token));
    Assertions.assertEquals(secretValue, configSource.getValue("telegram.secret"));
  }
}

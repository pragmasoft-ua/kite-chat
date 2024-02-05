/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// TODO: 05.02.2024 Add tests
class SsmParameterConfigurationTest {

  private static final String BASE_NAME = "ssm.parameter.store";
  private static final String DEFAULT_DELIMITER = "-";

  private static final String PARAMETER_LIST = BASE_NAME + ".available.parameters";
  private static final String ENVIRONMENT = BASE_NAME + ".env";
  private static final String ENVIRONMENT_DELIMITER = BASE_NAME + ".env.delimiter";
  private static final String ENV_DEPENDENT_PARAMS = BASE_NAME + ".env.dependent.parameters";

  private ConfigSourceContext context;
  private ConfigValue configValue;

  @BeforeEach
  void init() {
    this.context = mock(ConfigSourceContext.class);
    this.configValue = mock(ConfigValue.class);
  }

  @Test
  void shouldParseEnv() {
    doReturn(configValue).when(context).getValue(ENVIRONMENT);
    doReturn("dev").when(configValue).getValue();

    var configuration = new SsmParameterConfiguration(context);

    Assertions.assertEquals("dev", configuration.getEnv());
  }
}

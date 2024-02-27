/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.model.GetParametersRequest;
import software.amazon.awssdk.services.ssm.model.Parameter;

public class SsmConfigSource implements ConfigSource {

  private static final Logger log = LoggerFactory.getLogger(SsmConfigSource.class);

  private final SsmParameterConfiguration configuration;
  private final Map<String, String> parameters;

  private SsmConfigSource(SsmParameterConfiguration configuration, Map<String, String> parameters) {
    this.configuration = configuration;
    this.parameters = parameters;
  }

  public static SsmConfigSource create(
      SsmParameterConfiguration configuration, SsmClient ssmClient) {
    Map<String, String> paramsMap = new HashMap<>();
    Set<String> availableParameters = configuration.getAvailableParameters();
    Set<String> dependentParameters = configuration.getDependentParameters();
    availableParameters.addAll(dependentParameters);

    if (!availableParameters.isEmpty()) {
      GetParametersRequest getParametersRequest =
          GetParametersRequest.builder().names(availableParameters).withDecryption(true).build();
      List<Parameter> parameters = ssmClient.getParameters(getParametersRequest).parameters();

      parameters.forEach(
          parameter -> {
            String name = parameter.name();
            if (dependentParameters.contains(name)) {
              name = name.replace(configuration.getEnv() + configuration.getEnvDelimiter(), "");
            }

            paramsMap.put(name, parameter.value());
          });

      List<String> parameterNames = parameters.stream().map(Parameter::name).toList();
      List<String> notFoundParameters =
          availableParameters.stream().filter(p -> !parameterNames.contains(p)).toList();

      if (!notFoundParameters.isEmpty()) {
        log.warn(
            "Failed to retrieve all specified parameters. Expected number of parameters {} but was"
                + " {}.",
            availableParameters.size(),
            paramsMap.size());
        log.warn("{} were not found in SSM", notFoundParameters);
      }
    }

    return new SsmConfigSource(configuration, paramsMap);
  }

  @Override
  public Set<String> getPropertyNames() {
    return parameters.keySet();
  }

  @Override
  public String getValue(String propertyName) {
    return parameters.get(propertyName);
  }

  @Override
  public String getName() {
    return configuration.getConfigName();
  }

  @Override
  public int getOrdinal() {
    return 501;
  }
}

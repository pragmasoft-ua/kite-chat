/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import io.smallrye.config.ConfigSourceContext;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SsmParameterConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SsmParameterConfiguration.class);

  private static final String BASE_NAME = "ssm.parameter.store";
  private static final String DEFAULT_DELIMITER = "-";

  private static final String PARAMETER_LIST = BASE_NAME + ".available.parameters";
  private static final String ENVIRONMENT = BASE_NAME + ".env";
  private static final String ENVIRONMENT_DELIMITER = BASE_NAME + ".env.delimiter";
  private static final String ENV_DEPENDENT_PARAMS = BASE_NAME + ".env.dependent.parameters";

  private final ConfigSourceContext context;
  private final Set<String> availableParameters = new HashSet<>();
  private final Set<String> dependentParameters = new HashSet<>();
  private String env;
  private String envDelimiter;

  // TODO: 02.02.2024 We can also add configuration of AWS client, region
  public SsmParameterConfiguration(ConfigSourceContext context) {
    this.context = context;

    initEnvProperty();
    initEnvDelimiterProperty();
    initAvailableParameters();
    initDependentParameters();
  }

  private void initEnvProperty() {
    this.initProperty(
        ENVIRONMENT,
        envValue -> {
          this.env = envValue;
          log.info("ENVIRONMENT is {}", envValue);
        });
  }

  private void initEnvDelimiterProperty() {
    this.initProperty(
        ENVIRONMENT_DELIMITER,
        delimiterValue -> {
          if (this.env == null) {
            log.warn("Environment delimiter is specified but there is no {}", ENVIRONMENT);
          } else {
            this.envDelimiter = delimiterValue;
          }
        },
        () -> {
          if (this.env != null) {
            log.info(
                "Environment delimiter is not specified, default [{}] will be used",
                DEFAULT_DELIMITER);
            this.envDelimiter = DEFAULT_DELIMITER;
          }
        });
  }

  private void initAvailableParameters() {
    this.initProperty(
        PARAMETER_LIST,
        parametersValue -> {
          String[] params = parametersValue.trim().split(",");
          this.availableParameters.addAll(Arrays.asList(params));
          log.debug("{} will be retrieved from SSM Parameter Store", this.availableParameters);
        },
        () ->
            log.warn(
                "No SSM parameters is specified. Utilize [{}] property to specify names of the SSM"
                    + " parameters to be retrieved.",
                PARAMETER_LIST));
  }

  private void initDependentParameters() {
    this.initProperty(
        ENV_DEPENDENT_PARAMS,
        dependentParamsValue -> {
          if (this.env == null) {
            log.warn("{} is specified but there is no {}", ENV_DEPENDENT_PARAMS, ENVIRONMENT);
          } else {
            String[] dependentParams = dependentParamsValue.trim().split(",");
            List<String> envDependentParams =
                Stream.of(dependentParams).map(p -> env + envDelimiter + p).toList();
            this.dependentParameters.addAll(envDependentParams);
            log.debug("{} will be retrieved from SSM Parameter Store", this.dependentParameters);
          }
        });
  }

  private void initProperty(String prop, Consumer<String> callback, Action onFailure) {
    String value = this.context.getValue(prop).getValue();
    if (value != null && !value.isBlank()) {
      callback.accept(value);
    } else {
      onFailure.doAction();
    }
  }

  private void initProperty(String prop, Consumer<String> callback) {
    this.initProperty(prop, callback, () -> {});
  }

  public Set<String> getAvailableParameters() {
    return this.availableParameters;
  }

  public Set<String> getDependentParameters() {
    return this.dependentParameters;
  }

  public String getEnv() {
    return env;
  }

  public String getEnvDelimiter() {
    return envDelimiter;
  }

  public String getConfigName() {
    return BASE_NAME;
  }

  @FunctionalInterface
  private interface Action {
    void doAction();
  }
}

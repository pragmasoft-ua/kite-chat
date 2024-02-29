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

/**
 * This class is used to configure {@link SsmConfigSource} behavior. It uses the following
 * properties:
 * <li>ssm.parameter.store.available.parameters - List of parameters' names which will be retrieved
 *     from SSM.
 * <li>ssm.parameter.store.env - The environment name that is used for "env.dependent.parameters".
 *     Value from "quarkus.profile" is used by default.
 * <li>ssm.parameter.store.env.delimiter - The environment delimiter (Default is "-") that is used
 *     for "env.dependent.parameters".
 * <li>ssm.parameter.store.env.dependent.parameters - List of parameters' names which depend on env.
 *     <br>
 *     For example parameter "telegram.token" is used in the application, but this parameter is
 *     different based on env, so that in SSM there are two instances of this parameter:
 *     "dev-telegram.token" and "prod-telegram.token", in order to have only one abstract name of
 *     such a parameter ("telegram.token") in application you can make use of
 *     "ssm.parameter.store.env.dependent.parameters" property. In this case it will look for
 *     specified parameter based on provided "ssm.parameter.store.env" +
 *     "ssm.parameter.store.env.delimiter" For instance, if you specify "telegram.token" as
 *     dependent, "prod" as env and "-" as a delimiter, it will look for "prod-telegram.token" in
 *     SSM, if such parameter was found you can access it by the name provided in
 *     "ssm.parameter.store.env.dependent.parameters", like "telegram.token"
 */
public class SsmParameterConfigurationImpl implements SsmParameterConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SsmParameterConfigurationImpl.class);

  private static final String BASE_NAME = "ssm.parameter.store";
  private static final String DEFAULT_DELIMITER = "-";

  private static final String QUARKUS_PROFILE = "quarkus.profile";
  private static final String PARAMETER_LIST = BASE_NAME + ".available.parameters";
  private static final String ENVIRONMENT = BASE_NAME + ".env";
  private static final String ENVIRONMENT_DELIMITER = BASE_NAME + ".env.delimiter";
  private static final String ENV_DEPENDENT_PARAMS = BASE_NAME + ".env.dependent.parameters";

  private final ConfigSourceContext context;
  private final Set<String> availableParameters = new HashSet<>();
  private final Set<String> dependentParameters = new HashSet<>();
  private String env;
  private String envDelimiter;

  public SsmParameterConfigurationImpl(ConfigSourceContext context) {
    this.context = context;

    initEnvProperty();
    initEnvDelimiterProperty();
    initDependentParameters();
    initAvailableParameters();
  }

  private void initEnvProperty() {
    this.initProperty(
        ENVIRONMENT,
        envValue -> this.env = envValue,
        () -> this.initProperty(QUARKUS_PROFILE, envValue -> this.env = envValue));
    log.info("ENVIRONMENT is {}", this.env);
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

  private void initDependentParameters() {
    this.initProperty(
        ENV_DEPENDENT_PARAMS,
        paramValue -> {
          if (this.env == null) {
            log.warn("{} is specified but there is no {}", ENV_DEPENDENT_PARAMS, ENVIRONMENT);
          } else {
            String[] dependentParams = this.parseParameterList(paramValue);
            List<String> envDependentParams =
                Stream.of(dependentParams).map(p -> env + envDelimiter + p).toList();
            this.dependentParameters.addAll(envDependentParams);
            log.info("{} will be retrieved from SSM Parameter Store", this.dependentParameters);
          }
        });
  }

  private void initAvailableParameters() {
    this.initProperty(
        PARAMETER_LIST,
        paramValue -> {
          String[] params = this.parseParameterList(paramValue);
          this.availableParameters.addAll(Arrays.asList(params));
          log.info("{} will be retrieved from SSM Parameter Store", this.availableParameters);
        },
        () -> {
          if (dependentParameters.isEmpty()) {
            log.info(
                "No SSM parameters is specified. Utilize [{} or {}] property to specify names of"
                    + " the SSM parameters to be retrieved.",
                PARAMETER_LIST,
                ENV_DEPENDENT_PARAMS);
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

  private String[] parseParameterList(String listOfParameters) {
    return listOfParameters.replaceAll("\\s", "").split(",");
  }

  @Override
  public Set<String> getAvailableParameters() {
    return this.availableParameters;
  }

  @Override
  public Set<String> getDependentParameters() {
    return this.dependentParameters;
  }

  @Override
  public String getEnv() {
    return env;
  }

  @Override
  public String getEnvDelimiter() {
    return envDelimiter;
  }

  @Override
  public String getConfigName() {
    return BASE_NAME;
  }

  @FunctionalInterface
  private interface Action {
    void doAction();
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SsmParameterConfigurationImplTest {

  private static final String BASE_NAME = "ssm.parameter.store";
  private static final String DEFAULT_DELIMITER = "-";

  private static final String QUARKUS_PROFILE = "quarkus.profile";
  private static final String PARAMETER_LIST = BASE_NAME + ".available.parameters";
  private static final String ENVIRONMENT = BASE_NAME + ".env";
  private static final String ENVIRONMENT_DELIMITER = BASE_NAME + ".env.delimiter";
  private static final String ENV_DEPENDENT_PARAMS = BASE_NAME + ".env.dependent.parameters";

  private ConfigSourceContext context;
  private ConfigValue envConfigValue;
  private ConfigValue quarkusProfile;
  private ConfigValue delimiterConfigValue;
  private ConfigValue availableParmaetersConfigValue;
  private ConfigValue dependentParmaetersConfigValue;

  @BeforeEach
  void init() {
    this.context = mock(ConfigSourceContext.class);
    this.envConfigValue = mock(ConfigValue.class);
    this.quarkusProfile = mock(ConfigValue.class);
    this.delimiterConfigValue = mock(ConfigValue.class);
    this.availableParmaetersConfigValue = mock(ConfigValue.class);
    this.dependentParmaetersConfigValue = mock(ConfigValue.class);

    doReturn(envConfigValue).when(context).getValue(ENVIRONMENT);
    doReturn(quarkusProfile).when(context).getValue(QUARKUS_PROFILE);
    doReturn(delimiterConfigValue).when(context).getValue(ENVIRONMENT_DELIMITER);
    doReturn(availableParmaetersConfigValue).when(context).getValue(PARAMETER_LIST);
    doReturn(dependentParmaetersConfigValue).when(context).getValue(ENV_DEPENDENT_PARAMS);
  }

  @Test
  void should_init_env() {
    String env = "dev";
    doReturn(env).when(envConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(env, configuration.getEnv());
  }

  @Test
  void should_init_env_from_profile_if_not_set() {
    String env = "dev";
    doReturn(env).when(quarkusProfile).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(env, configuration.getEnv());
  }

  @Test
  void should_init_env_delimiter() {
    String env = "prod";
    String delimiter = ".";

    doReturn(env).when(envConfigValue).getValue();
    doReturn(delimiter).when(delimiterConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(env, configuration.getEnv());
    Assertions.assertEquals(delimiter, configuration.getEnvDelimiter());
  }

  @Test
  void should_not_init_env_delimiter_if_env_is_not_specified() {
    doReturn("-").when(delimiterConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertNull(configuration.getEnv());
    Assertions.assertNull(configuration.getEnvDelimiter());
  }

  @Test
  void should_use_default_env_delimiter_if_it_is_not_specified() {
    String env = "prod";

    doReturn(env).when(envConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(env, configuration.getEnv());
    Assertions.assertEquals(DEFAULT_DELIMITER, configuration.getEnvDelimiter());
  }

  @Test
  void should_init_dependent_parameters() {
    String env = "prod";
    String parameters = "telegram.token, app.name,app.some.value";
    Set<String> expectedParams =
        Set.of(
            env + DEFAULT_DELIMITER + "telegram.token",
            env + DEFAULT_DELIMITER + "app.name",
            env + DEFAULT_DELIMITER + "app.some.value");

    doReturn(env).when(envConfigValue).getValue();
    doReturn(parameters).when(dependentParmaetersConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(env, configuration.getEnv());
    Assertions.assertEquals(expectedParams, configuration.getDependentParameters());
  }

  @Test
  void should_not_init_dependent_parameters_if_env_is_not_set() {
    String parameters = "telegram.token, app.name,app.some.value";

    doReturn(parameters).when(dependentParmaetersConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertNull(configuration.getEnv());
    Assertions.assertEquals(Collections.emptySet(), configuration.getDependentParameters());
  }

  @Test
  void should_init_available_parameters() {
    String parameters = "telegram.token, app.name,app.some.value";
    Set<String> expectedParams = Set.of("telegram.token", "app.name", "app.some.value");

    doReturn(parameters).when(availableParmaetersConfigValue).getValue();

    var configuration = new SsmParameterConfigurationImpl(context);

    Assertions.assertEquals(expectedParams, configuration.getAvailableParameters());
  }
}

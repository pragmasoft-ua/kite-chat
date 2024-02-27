/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClientBuilder;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import java.util.Collections;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 * Adds custom ConfigSource that retrieves parameters from SSM
 *
 * @see SsmConfigSource
 */
public class SsmConfigSourceFactory implements ConfigSourceFactory {

  private static final String RUNTIME_CLASS = "io.quarkus.deployment.steps.RuntimeConfigSetup";

  /**
   * Creates {@link SsmConfigSource} and Skip static init phase to prevent unnecessary
   * initialization
   */
  @Override
  public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
    if (isRuntimePhase()) {
      return Collections.singleton(
          SsmConfigSource.create(
              new SsmParameterConfigurationImpl(context),
              AWSSimpleSystemsManagementAsyncClientBuilder.defaultClient()));
    }

    return Collections.emptyList();
  }

  /** Check whether it's Runtime phase or not. */
  private boolean isRuntimePhase() {
    try {
      Class.forName(RUNTIME_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}

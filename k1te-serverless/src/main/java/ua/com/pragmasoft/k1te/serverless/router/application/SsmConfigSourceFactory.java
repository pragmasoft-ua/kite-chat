/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsyncClientBuilder;
import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import java.util.Collections;
import java.util.List;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class SsmConfigSourceFactory implements ConfigSourceFactory {

  private static final String RUNTIME_CLASS = "io.quarkus.deployment.steps.RuntimeConfigSetup";

  @Override
  public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
    if (isRuntimePhase()) {
      return List.of(
          SsmConfigSource.create(
              new SsmParameterConfiguration(context),
              AWSSimpleSystemsManagementAsyncClientBuilder.defaultClient()));
    }

    return Collections.emptyList();
  }

  private boolean isRuntimePhase() {
    try {
      Class.forName(RUNTIME_CLASS);
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.serverless.router.application;

import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import java.util.stream.Collectors;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;

/**
 * This class eagerly initializes all connectors, not only those injected to the active lambda
 * handler
 *
 * <p>https://quarkus.io/guides/cdi-reference#eager-instantiation-of-beans
 */
@ApplicationScoped
public class ConnectorsInitializer {

  private Instance<Connector> connectors;

  /** */
  @Inject
  public ConnectorsInitializer(final Instance<Connector> connectors) {
    this.connectors = connectors;
  }

  /**
   * Need this method to eagerly initialize connectors
   *
   * @param event
   */
  void startup(@Observes StartupEvent event) {
    Log.debug(
        "Initialized connectors: "
            + this.connectors.stream().map(Connector::id).collect(Collectors.joining(",")));
  }
}

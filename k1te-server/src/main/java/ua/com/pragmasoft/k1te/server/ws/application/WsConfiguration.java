package ua.com.pragmasoft.k1te.server.ws.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;

public class WsConfiguration {

  @Produces
  @ApplicationScoped
  public WsConnector websocketConnector(Router router, Channels channels,
      WsConnector.WsConnectionRegistry connections) {
    return new WsConnector(router, channels, connections);
  }

}

package ua.com.pragmasoft.k1te.server.ws.application;

import org.eclipse.microprofile.config.inject.ConfigProperty;

import jakarta.enterprise.context.ApplicationScoped;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.backend.ws.infrastructure.S3ObjectStore;

public class WsConfiguration {

  @ApplicationScoped
  public S3ObjectStore objectStore(@ConfigProperty(name = "bucket.name") String bucketName) {
    return new S3ObjectStore(bucketName);
  }

  @ApplicationScoped
  public WsConnector websocketConnector(Router router, Channels channels,
      WsConnector.WsConnectionRegistry connections, ObjectStore objectStore) {
    return new WsConnector(router, channels, connections, objectStore);
  }

}

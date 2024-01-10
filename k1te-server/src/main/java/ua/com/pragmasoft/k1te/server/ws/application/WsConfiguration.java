/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.server.ws.application;

import io.quarkus.arc.DefaultBean;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Messages;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.backend.ws.infrastructure.S3ObjectStore;

public class WsConfiguration {

  @Produces
  @ApplicationScoped
  @DefaultBean
  public S3ObjectStore objectStore(
      @ConfigProperty(name = "bucket.name") String bucketName,
      S3Client s3Client,
      S3Presigner presigner) {
    return new S3ObjectStore(bucketName, s3Client, presigner);
  }

  @Produces
  @ApplicationScoped
  @DefaultBean
  public FileSystem fileSystem() {
    return FileSystems.getDefault();
  }

  @ApplicationScoped
  @Produces
  public WsConnector websocketConnector(
      Router router,
      Channels channels,
      Messages messages,
      WsConnector.WsConnectionRegistry connections,
      ObjectStore objectStore) {
    return new WsConnector(router, channels, messages, connections, objectStore);
  }
}

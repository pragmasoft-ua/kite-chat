package ua.com.pragmasoft.k1te.serverless.ws.application;

import java.net.URI;
import java.net.URISyntaxException;

import io.quarkus.runtime.Startup;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.s3.S3Client;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector.WsConnectionRegistry;
import ua.com.pragmasoft.k1te.backend.ws.infrastructure.S3ObjectStore;

public class WsConfiguration {


  @Produces
  @ApplicationScoped
  @SuppressWarnings("java:S6241") // Region is encoded in the endpoint uri.
  public ApiGatewayManagementApiClient apiClient(
      @ConfigProperty(name = "ws.api.execution.endpoint") URI wsApiExecutionEndpoint) {
    if (wsApiExecutionEndpoint.getScheme().equals("wss")) {
      try {
        wsApiExecutionEndpoint = new URI("https",wsApiExecutionEndpoint.getSchemeSpecificPart(),wsApiExecutionEndpoint.getFragment());
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }
    Log.infof("ApiGatewayManagementApiClient endpoint: %s", wsApiExecutionEndpoint);
    return ApiGatewayManagementApiClient
        .builder()
        .httpClient(UrlConnectionHttpClient.builder().build())
        .credentialsProvider(DefaultCredentialsProvider.create())
        .endpointOverride(wsApiExecutionEndpoint)
        .build();
  }

  @Produces
  @ApplicationScoped
  public AwsApiGwConnectionRegistry awsApiGwConnectionRegistry(ApiGatewayManagementApiClient apiClient) {
    return new AwsApiGwConnectionRegistry(apiClient);
  }

  @ApplicationScoped
  public S3ObjectStore objectStore(@ConfigProperty(name = "bucket.name") String bucketName, S3Client s3Client) {
    return new S3ObjectStore(bucketName, s3Client);
  }

  @Produces
  @ApplicationScoped
  public WsConnector websocketConnector(Router router, Channels channels,
      WsConnectionRegistry connections, ObjectStore objectStore) {
    return new WsConnector(router, channels, connections, objectStore);
  }

}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.ws.application;

import java.io.IOException;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.services.apigatewaymanagementapi.ApiGatewayManagementApiClient;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.DeleteConnectionRequest;
import software.amazon.awssdk.services.apigatewaymanagementapi.model.PostToConnectionRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.PayloadEncoder;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector.WsConnection;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector.WsConnectionRegistry;

public final class AwsApiGwConnectionRegistry implements WsConnectionRegistry {

  private static final PayloadEncoder ENCODER = new PayloadEncoder();

  private final ApiGatewayManagementApiClient apiClient;

  /**
   * @param apiClient
   */
  public AwsApiGwConnectionRegistry(ApiGatewayManagementApiClient apiClient) {
    this.apiClient = apiClient;
  }

  final class AwsApiGwWebsocketConnection implements WsConnection {

    private final String connectionUri;

    /**
     * @param connectionUri
     */
    public AwsApiGwWebsocketConnection(String connectionUri) {
      this.connectionUri = connectionUri;
    }

    @Override
    public void close() throws IOException {
      final var request =
          DeleteConnectionRequest.builder().connectionId(this.connectionUri).build();
      AwsApiGwConnectionRegistry.this.apiClient.deleteConnection(request);
    }

    @Override
    public String connectionUri() {
      return this.connectionUri;
    }

    @Override
    public void sendObject(Payload payload) throws IOException {
      final var serializedPayload = SdkBytes.fromUtf8String(ENCODER.apply(payload));
      final var request =
          PostToConnectionRequest.builder()
              .connectionId(this.connectionUri)
              .data(serializedPayload)
              .build();
      AwsApiGwConnectionRegistry.this.apiClient.postToConnection(request);
    }
  }

  @Override
  public WsConnection getConnection(String connectionUri) {
    return new AwsApiGwWebsocketConnection(connectionUri);
  }
}

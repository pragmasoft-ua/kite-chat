/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;
import io.quarkus.logging.Log;
import jakarta.inject.Named;
import java.util.Map;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.PayloadDecoder;
import ua.com.pragmasoft.k1te.backend.ws.PayloadEncoder;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.serverless.ws.application.AwsApiGwConnectionRegistry;

@Named("ws")
public class WsHandler
    implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

  private static final PayloadDecoder DECODER = new PayloadDecoder();
  private static final PayloadEncoder ENCODER = new PayloadEncoder();

  private final WsConnector wsConnector;
  private final AwsApiGwConnectionRegistry connectionRegistry;

  /**
   * @param wsConnector
   * @param connectionRegistry
   */
  public WsHandler(WsConnector wsConnector, AwsApiGwConnectionRegistry connectionRegistry) {
    this.wsConnector = wsConnector;
    this.connectionRegistry = connectionRegistry;
  }

  @Override
  public final APIGatewayV2WebSocketResponse handleRequest(
      APIGatewayV2WebSocketEvent input, Context context) {
    Log.debug(input.toString());
    final var eventType = input.getRequestContext().getEventType();
    final var connectionId = input.getRequestContext().getConnectionId();
    final var body = input.getBody();
    final var connection = this.connectionRegistry.getConnection(connectionId);
    String channelName = null;
    String memberId = null;
    if (input.getQueryStringParameters() != null) {
      channelName = input.getQueryStringParameters().get("c");
      memberId = input.getQueryStringParameters().get("m");
    }
    Payload responsePayload;
    try {
      responsePayload =
          switch (eventType) {
            case "CONNECT" -> this.wsConnector.onOpen(connection, channelName, memberId);
            case "DISCONNECT" -> this.wsConnector.onClose(connection);
            case "MESSAGE" -> this.wsConnector.onPayload(DECODER.apply(body), connection);
            default -> throw new IllegalStateException("Unsupported event type: " + eventType);
          };
    } catch (Exception e) {
      responsePayload = this.wsConnector.onError(connection, e);
    }
    final var response = new APIGatewayV2WebSocketResponse();
    response.setHeaders(Map.of("Sec-WebSocket-Protocol", WsConnector.SUBPROTOCOL));
    if (null != responsePayload) {
      response.setBody(ENCODER.apply(responsePayload));
    }
    response.setStatusCode(200);
    Log.debugf("ws %s (%s) -> %s", input, context, response);
    return response;
  }
}

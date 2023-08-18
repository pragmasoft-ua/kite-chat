package ua.com.pragmasoft.kite.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

import io.quarkus.logging.Log;
import jakarta.inject.Named;

@Named("ws")
public class WsHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

  @Override
  public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
    final var response = new APIGatewayV2WebSocketResponse();
    response.setStatusCode(200);
    var routeKey = input.getRequestContext().getRouteKey();
    switch (routeKey) {
      case "$connect":
        response.setHeaders(Map.of("Sec-WebSocket-Protocol", "v1.k1te.chat"));
        break;
      case "$disconnect":
        break;
      default:
        response.setBody("OK");
    }
    response.setStatusCode(200);
    Log.infof("ws %s -> %s (%s)", input, response, context);
    return response;
  }
}

package ua.com.pragmasoft.kite.handler;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketResponse;

import io.quarkus.logging.Log;

@Named("ws")
public class WsHandler implements RequestHandler<APIGatewayV2WebSocketEvent, APIGatewayV2WebSocketResponse> {

  @Inject
  ProcessingService service;

  @Override
  public APIGatewayV2WebSocketResponse handleRequest(APIGatewayV2WebSocketEvent input, Context context) {
    final var response = new APIGatewayV2WebSocketResponse();
    response.setStatusCode(200);
    Log.infof("ws %s -> %s (%s)", input, response, context);
    return response;
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2WebSocketEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.serverless.handler.event.LambdaEvent;

@Named(value = "main")
public class RequestDispatcher implements RequestStreamHandler {

  private final ObjectMapper objectMapper;
  private final TgWebhook tg;
  private final WsHandler ws;

  @Inject
  public RequestDispatcher(ObjectMapper objectMapper, TgWebhook tg, WsHandler ws) {
    this.objectMapper = objectMapper;
    this.tg = tg;
    this.ws = ws;
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    final LambdaEvent lambdaEvent;
    try (input) {
      lambdaEvent = objectMapper.readValue(input, LambdaEvent.class);
    } catch (Exception exception) {
      throw new KiteException("Event deserialization error", exception);
    }
    final Object response;
    if (lambdaEvent instanceof APIGatewayV2HTTPEvent httpEvent) {
      response = this.tg.handleRequest(httpEvent, context);
    } else if (lambdaEvent instanceof APIGatewayV2WebSocketEvent wsEvent) {
      response = this.ws.handleRequest(wsEvent, context);
    } else {
      throw new KiteException("Unsupported event: " + lambdaEvent);
    }
    objectMapper.writeValue(output, response);
  }
}

package ua.com.pragmasoft.k1te.serverless.handler;

import java.util.Map;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import software.amazon.awssdk.http.HttpStatusCode;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;

@ApplicationScoped
@Named("tg")
public class TgWebhook implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private final TelegramConnector connector;

  /**
   * @param connections
   */
  public TgWebhook(final TelegramConnector connector) {
    this.connector = connector;
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
    final var requestBody = input.getBody();
    final var response = new APIGatewayV2HTTPResponse();
    response.setHeaders(Map.of("Content-Type", "text/plain"));
    try {
      Log.debug(">> " + requestBody);
      Update update = BotUtils.parseUpdate(requestBody);
      var responseBody = this.connector.onUpdate(update);
      Log.debug("<< " + responseBody);
      response.setBody(responseBody);
      response.setStatusCode(HttpStatusCode.OK);
    } catch (Exception e) {
      response.setBody(e.getLocalizedMessage());
      response.setStatusCode(HttpStatusCode.INTERNAL_SERVER_ERROR);
    }
    return response;
  }

}

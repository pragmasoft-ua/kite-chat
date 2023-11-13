/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayV2HTTPResponse;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Named;
import java.util.Map;
import software.amazon.awssdk.http.HttpStatusCode;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;

@ApplicationScoped
@Named("tg")
public class TgWebhook implements RequestHandler<APIGatewayV2HTTPEvent, APIGatewayV2HTTPResponse> {

  private final TelegramConnector connector;

  public TgWebhook(final TelegramConnector connector) {
    this.connector = connector;
  }

  @Override
  public APIGatewayV2HTTPResponse handleRequest(APIGatewayV2HTTPEvent input, Context context) {
    final var requestBody = input.getBody();
    Log.debug(">> " + requestBody);
    Update update = BotUtils.parseUpdate(requestBody);
    var responseBody = this.connector.onUpdate(update);
    Log.debug("<< " + responseBody);
    return APIGatewayV2HTTPResponse.builder()
        .withStatusCode(HttpStatusCode.OK)
        .withHeaders(Map.of("Content-Type", "application/json"))
        .withBody(responseBody)
        .build();
  }
}

package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;
import ua.com.pragmasoft.k1te.serverless.handler.Lifecycle.Action;

@Named("lifecycle")
public class LifecycleLambda implements RequestHandler<Lifecycle, String> {

  private final TelegramConnector telegramConnector;

  /**
   * @param telegramConnector
   */
  @Inject
  public LifecycleLambda(TelegramConnector telegramConnector) {
    this.telegramConnector = telegramConnector;
  }

  @Override
  public String handleRequest(Lifecycle input, Context context) {
    Action action = Action.valueOf(input.tf().action());
    var result = switch (action) {
      case create -> registerWebhook();
      case update -> registerWebhook();
      case delete -> unregisterWebhook();
    };
    Log.infof("Lifecycle action %s -> %s", action, result);
    return result;
  }

  private String registerWebhook() {
    var webhookUri = this.telegramConnector.setWebhook();
    return "Registered telegram webhook " + webhookUri;
  }

  private String unregisterWebhook() {
    this.telegramConnector.close();
    return "Unregistered telegram webhook";
  }
}

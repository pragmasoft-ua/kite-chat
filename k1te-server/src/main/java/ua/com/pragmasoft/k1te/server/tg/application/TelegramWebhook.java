/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.server.tg.application;

import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.faulttolerance.api.RateLimit;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.temporal.ChronoUnit;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.resteasy.reactive.RestHeader;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;

@ApplicationScoped
@Path(TelegramWebhook.WEBHOOK_PATH)
public class TelegramWebhook {

  public static final String WEBHOOK_PATH = "/api/telegram";
  private final String secretToken;
  private final TelegramConnector connector;

  @Inject
  public TelegramWebhook(
      @ConfigProperty(name = "telegram.secret.token") String secretToken,
      final TelegramConnector connector) {
    this.secretToken = secretToken;
    this.connector = connector;
  }

  /**
   * Need this method to eagerly initialize controller and register web hook
   *
   * @param event
   */
  void startup(@Observes StartupEvent event) {
    connector.setWebhook();
  }

  /**
   * Need this method to eagerly initialize controller and register web hook
   *
   * @param event
   */
  void shutdown(@Observes ShutdownEvent event) {
    connector.close();
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @RateLimit(value = 600, window = 1, windowUnit = ChronoUnit.MINUTES)
  public String webhook(
      String requestBody, @RestHeader("X-Telegram-Bot-Api-Secret-Token") String token) {
    if (!secretToken.equals(token)) {
      Log.error("Security token is invalid: " + token);
      return "OK";
    }
    Log.debug(">> " + requestBody);
    Update update = BotUtils.parseUpdate(requestBody);
    var responseBody = this.connector.onUpdate(update);
    Log.debug("<< " + responseBody);
    return responseBody;
  }
}

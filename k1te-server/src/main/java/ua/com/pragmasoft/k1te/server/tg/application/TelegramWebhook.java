/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
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
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;

@ApplicationScoped
@Path(TelegramWebhook.WEBHOOK_PATH)
public class TelegramWebhook {

  public static final String WEBHOOK_PATH = "/api/telegram";
  private final TelegramConnector connector;

  /** */
  @Inject
  public TelegramWebhook(final TelegramConnector connector) {
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
  public String webhook(String requestBody) {
    Log.debug(">> " + requestBody);
    Update update = BotUtils.parseUpdate(requestBody);
    var responseBody = this.connector.onUpdate(update);
    Log.debug("<< " + responseBody);
    return responseBody;
  }
}

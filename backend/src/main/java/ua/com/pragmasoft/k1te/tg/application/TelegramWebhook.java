package ua.com.pragmasoft.k1te.tg.application;

import java.time.temporal.ChronoUnit;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.smallrye.faulttolerance.api.RateLimit;
import ua.com.pragmasoft.k1te.tg.TelegramConnector;

@ApplicationScoped
@Path(TelegramWebhook.WEBHOOK_PATH)
public class TelegramWebhook {

  public static final String WEBHOOK_PATH = "/api/telegram";
  final TelegramConnector connector;


  /**
   *
   */
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
    connector.setWebhook(WEBHOOK_PATH);
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

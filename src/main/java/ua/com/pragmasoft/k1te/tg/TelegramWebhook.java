package ua.com.pragmasoft.k1te.tg;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;
import javax.inject.Inject;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.model.Update;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;

@ApplicationScoped
@Path(TelegramWebhook.WEBHOOK_PATH)
public class TelegramWebhook {

  public static final String WEBHOOK_PATH = "/api/telegram";
  final TelegramConnector connector;
  public final String webhookUrl;


  /**
   *
   */
  @Inject
  public TelegramWebhook(final TelegramConnector connector,
      @ConfigProperty(name = "webhook.host") final URI webhookHost) {
    this.connector = connector;
    this.webhookUrl = webhookHost.resolve(WEBHOOK_PATH).toASCIIString();
  }

  /**
   * Need this method to eagerly initialize controller and register web hook
   * 
   * @param event
   */
  void startup(@Observes StartupEvent event) {
    connector.setWebhook(this.webhookUrl);
  }

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public String webhook(String requestBody) {
    Log.debug(requestBody);
    Update update = BotUtils.parseUpdate(requestBody);
    var responseBody = this.connector.onUpdate(update);
    Log.debug(responseBody);
    return responseBody;
  }

}

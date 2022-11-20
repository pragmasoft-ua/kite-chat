package ua.com.pragmasoft.k1te.tg.infrastructure;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Disposes;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.pengrad.telegrambot.TelegramBot;
import ua.com.pragmasoft.k1te.router.Router;
import ua.com.pragmasoft.k1te.tg.LastConversations;
import ua.com.pragmasoft.k1te.tg.TelegramConnector;

public class TelegramConfiguration {

  @Produces
  @ApplicationScoped
  public TelegramBot botClient(@ConfigProperty(name = "bot.token") String token) {
    return new TelegramBot(token);
  }

  @Produces
  @ApplicationScoped
  public LastConversations lastConversations() {
    return new InMemoryLastConversations();
  }

  @Produces
  @ApplicationScoped
  public TelegramConnector botConnector(TelegramBot botClient, Router router,
      @ConfigProperty(name = "webhook.host") final URI webhookHost,
      final LastConversations lastConversations) {
    return new TelegramConnector(botClient, router, webhookHost, lastConversations);
  }

  public void close(@Disposes TelegramConnector connector) {
    connector.close();
  }
}

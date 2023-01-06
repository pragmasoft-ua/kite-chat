package ua.com.pragmasoft.k1te.tg.infrastructure;

import java.net.URI;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import com.pengrad.telegrambot.TelegramBot;
import ua.com.pragmasoft.k1te.router.domain.Channels;
import ua.com.pragmasoft.k1te.router.domain.Router;
import ua.com.pragmasoft.k1te.tg.TelegramConnector;

public class TelegramConfiguration {

  @Produces
  @ApplicationScoped
  public TelegramBot botClient(@ConfigProperty(name = "bot.token") String token) {
    return new TelegramBot(token);
  }

  @Produces
  @ApplicationScoped
  public TelegramConnector botConnector(TelegramBot botClient, Router router, Channels channels,
      @ConfigProperty(name = "webhook.host") final URI base) {
    return new TelegramConnector(botClient, router, channels, base);
  }

}

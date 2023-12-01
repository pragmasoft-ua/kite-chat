/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.tg.application;

import com.pengrad.telegrambot.TelegramBot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import java.net.URI;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import okhttp3.OkHttpClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Messages;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;

public class TelegramConfiguration {

  @Produces
  @ApplicationScoped
  public OkHttpClient okHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(Duration.of(30, ChronoUnit.SECONDS))
        .writeTimeout(Duration.of(30, ChronoUnit.SECONDS))
        .readTimeout(Duration.of(30, ChronoUnit.SECONDS))
        .build();
  }

  @Produces
  @ApplicationScoped
  public TelegramBot botClient(
      @ConfigProperty(name = "telegram.bot.token") String token, OkHttpClient client) {
    return new TelegramBot.Builder(token).okHttpClient(client).build();
  }

  @Produces
  @ApplicationScoped
  public TelegramConnector botConnector(
      TelegramBot botClient,
      Router router,
      Channels channels,
      Messages messages,
      @ConfigProperty(name = "telegram.webhook.endpoint") final URI base,
      @ConfigProperty(name = "ws.api.execution.endpoint") final URI wsApi) {
    return new TelegramConnector(botClient, router, channels, messages, base, wsApi);
  }
}

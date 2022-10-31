package ua.com.pragmasoft.k1te.chat;

import java.time.Duration;

public record Chat(ChatId id, Route defaultRoute, Duration defaultConversationTimeout) {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofDays(30);

  public Chat(ChatId id, Route defaultRoute) {
    this(id, defaultRoute, DEFAULT_TIMEOUT);
  }

}

package ua.com.pragmasoft.k1te.router;

import java.time.Duration;
import java.time.Instant;

public record Conversation(ConversationId id, ChatId chat, Route client, Route operator,
    Instant created, Instant lastEvent, Duration timeout) {

  public boolean expired() {
    return Duration.between(lastEvent, Instant.now()).compareTo(this.timeout()) > 0;
  }

  public Conversation forward(Route newOperator) {
    return new Conversation(this.id, this.chat, this.client, newOperator, this.created,
        Instant.now(), this.timeout);
  }

}

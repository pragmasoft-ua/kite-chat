package ua.com.pragmasoft.k1te.chat;

import java.time.Instant;
import java.util.Optional;

public interface Message {

  MessageId id();

  Route origin();

  Instant timestamp();

  default Optional<ConversationId> conversationId() {
    return Optional.empty();
  }

}

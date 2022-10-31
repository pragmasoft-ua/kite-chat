package ua.com.pragmasoft.k1te.chat;

import java.util.Optional;

public interface LastConversations {

  void set(Route destination, ConversationId id);

  Optional<ConversationId> get(Route route);

}

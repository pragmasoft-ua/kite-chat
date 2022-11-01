package ua.com.pragmasoft.k1te.router;

import java.util.Optional;

public interface LastConversations {

  void set(Route destination, ConversationId id);

  Optional<ConversationId> get(Route route);

}

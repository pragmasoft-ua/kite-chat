package ua.com.pragmasoft.k1te.chat.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.com.pragmasoft.k1te.chat.ConversationId;
import ua.com.pragmasoft.k1te.chat.LastConversations;
import ua.com.pragmasoft.k1te.chat.Route;

public class InMemoryLastConversations implements LastConversations {

  final Map<Route, ConversationId> lastConversations = new ConcurrentHashMap<>();

  @Override
  public void set(Route destination, ConversationId id) {
    lastConversations.put(destination, id);
  }

  @Override
  public Optional<ConversationId> get(Route route) {
    return Optional.ofNullable(this.lastConversations.get(route));
  }

}

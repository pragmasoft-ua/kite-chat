package ua.com.pragmasoft.k1te.router.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.com.pragmasoft.k1te.router.ConversationId;
import ua.com.pragmasoft.k1te.router.LastConversations;
import ua.com.pragmasoft.k1te.router.Route;

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

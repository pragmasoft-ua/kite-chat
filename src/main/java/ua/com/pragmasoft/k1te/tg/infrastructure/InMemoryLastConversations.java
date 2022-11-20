package ua.com.pragmasoft.k1te.tg.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.com.pragmasoft.k1te.router.ConversationId;
import ua.com.pragmasoft.k1te.tg.LastConversations;

class InMemoryLastConversations implements LastConversations {

  final Map<Long, ConversationId> lastConversations = new ConcurrentHashMap<>();

  @Override
  public void set(Long chatId, ConversationId id) {
    lastConversations.put(chatId, id);
  }

  @Override
  public Optional<ConversationId> get(Long chatId) {
    return Optional.ofNullable(this.lastConversations.get(chatId));
  }

}

package ua.com.pragmasoft.k1te.tg;

import java.util.Optional;
import ua.com.pragmasoft.k1te.router.ConversationId;

public interface LastConversations {

  void set(Long chatId, ConversationId id);

  Optional<ConversationId> get(Long chatId);

}

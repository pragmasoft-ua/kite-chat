package ua.com.pragmasoft.k1te.router;

import java.util.Optional;

public interface ConversationRepository {

  Optional<Conversation> getById(ConversationId id);

  Conversation findOrCreateForClient(Route clientRoute, Chat chat);

  void finishConversation(Conversation conversation);

  void forwardConversation(Conversation conversation, Route newOperator);

  int deleteConversationsForChat(ChatId id);

}

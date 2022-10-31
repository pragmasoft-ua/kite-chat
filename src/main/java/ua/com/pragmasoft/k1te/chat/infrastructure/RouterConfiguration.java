package ua.com.pragmasoft.k1te.chat.infrastructure;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.inject.Produces;
import ua.com.pragmasoft.k1te.chat.ChatRepository;
import ua.com.pragmasoft.k1te.chat.ConversationRepository;
import ua.com.pragmasoft.k1te.chat.IdGenerator;
import ua.com.pragmasoft.k1te.chat.LastConversations;
import ua.com.pragmasoft.k1te.chat.Router;

public class RouterConfiguration {

  @Produces
  @ApplicationScoped
  public ChatRepository chatRepository() {
    return new InMemoryChatRepository();
  }

  @Produces
  @ApplicationScoped
  public ConversationRepository conversationRepository(IdGenerator idGenerator) {
    return new InMemoryConversationRepository(idGenerator);
  }

  @Produces
  @ApplicationScoped
  public LastConversations lastConversations() {
    return new InMemoryLastConversations();
  }

  @Produces
  @ApplicationScoped
  public Router router(ChatRepository chatRepository, ConversationRepository conversationRepository,
      LastConversations lastConversations) {
    return new Router(chatRepository, conversationRepository, lastConversations);
  }

  @Produces
  @ApplicationScoped
  public IdGenerator idGenerator() {
    return new NanoIdGenerator();
  }

}

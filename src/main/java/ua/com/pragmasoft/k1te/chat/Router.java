package ua.com.pragmasoft.k1te.chat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Router {

  final Map<ConnectorId, Connector> connectors = new HashMap<>(8);
  final ChatRepository chatRepository;
  final ConversationRepository conversationRepository;
  final LastConversations lastConversations;

  public Router(ChatRepository chatRepository, ConversationRepository conversationRepository,
      LastConversations lastConversations) {
    this.chatRepository = chatRepository;
    this.conversationRepository = conversationRepository;
    this.lastConversations = lastConversations;
  }

  public Conversation findOrCreateConversation(Route clientRoute, ChatId chatId) {
    return this.chatRepository.getChat(chatId)
        .map(chat -> this.conversationRepository.findOrCreateForClient(clientRoute, chat))
        .orElseThrow(() -> new IllegalArgumentException("Unknown chat: " + chatId.raw()));
  }

  public void finishConversation(Conversation conversation) {
    this.conversationRepository.finishConversation(conversation);
  }

  public void forwardConversation(Conversation conversation, Route newOperator) {
    this.conversationRepository.forwardConversation(conversation, newOperator);
  }

  public Optional<Chat> getChat(ChatId id) {
    return this.chatRepository.getChat(id);
  }

  public Chat createChat(Chat chat) {
    return this.chatRepository.createChat(chat);
  }

  public Chat updateChat(Chat chat) {
    return this.chatRepository.updateChat(chat);
  }

  public Chat deleteChat(ChatId id) {
    this.conversationRepository.deleteConversationsForChat(id);
    return this.chatRepository.deleteChat(id);

  }

  public synchronized Connector register(Connector connector) {
    this.connectors.put(connector.id(), connector);
    return connector;
  }

  public CompletableFuture<MessageResponse> sendMessageAsync(final TextMessageRequest m) {
    return m.conversationId()
    // @formatter:off
      .or(() -> this.lastConversations.get(m.origin()))
      .flatMap(this.conversationRepository::getById)
      .map(conversation -> this.route(m, conversation))
      .orElseGet(()->CompletableFuture.failedFuture(new IllegalStateException("No route")));
    // @formatter:on
  }

  protected <T extends Request<T, R>, R extends Response> CompletableFuture<R> route(T request,
      Conversation conversation) {
    final var source = request.origin();
    final var destination =
        conversation.client().equals(source) ? conversation.operator() : conversation.client();
    final var connector = this.connectors.get(destination.connectorId());
    if (null == connector) {
      return CompletableFuture.failedFuture(
          new IllegalStateException("Cannot find connector id " + destination.connectorId().raw()));
    }
    this.lastConversations.set(destination, conversation.id());
    return connector.sendAsync(request, destination, conversation);
  }

}

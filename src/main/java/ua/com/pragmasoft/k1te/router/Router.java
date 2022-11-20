package ua.com.pragmasoft.k1te.router;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

public class Router implements IRouter {

  final Map<ConnectorId, IConnector> connectors = new HashMap<>(8);
  final ChatRepository chatRepository;
  final ConversationRepository conversationRepository;

  public Router(ChatRepository chatRepository, ConversationRepository conversationRepository) {
    this.chatRepository = chatRepository;
    this.conversationRepository = conversationRepository;
  }

  @Override
  public Conversation conversation(Route client, ChatId chatId) {
    return this.chatRepository.getChat(chatId)
        .map(chat -> this.conversationRepository.findOrCreateForClient(client, chat))
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

  public synchronized IConnector register(IConnector connector) {
    this.connectors.put(connector.id(), connector);
    return connector;
  }

  @Override
  public CompletableFuture<RoutingResponse> routeAsync(RoutingRequest routingRequest) {
    final RoutingContext ctx = new RoutingContext(routingRequest);
    ctx.conversation = this.conversationRepository.getById(routingRequest.conversationId())
        .orElseThrow(() -> new IllegalStateException(
            "No conversation found for id " + routingRequest.conversationId().raw()));
    final var source = routingRequest.origin();
    ctx.destination = ctx.conversation.client().equals(source) ? ctx.conversation.operator()
        : ctx.conversation.client();
    final var connector = this.connectors.get(ctx.destination.connectorId());
    if (null == connector) {
      return CompletableFuture.failedFuture(new IllegalStateException(
          "No connector found for id " + ctx.destination.connectorId().raw()));
    }
    return connector.dispatchAsync(ctx).thenApply(RoutingContext::getRoutingResponse);
  }


}

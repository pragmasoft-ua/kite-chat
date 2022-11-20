package ua.com.pragmasoft.k1te.ws;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import io.quarkus.logging.Log;
import ua.com.pragmasoft.k1te.router.ChatId;
import ua.com.pragmasoft.k1te.router.ConnectorId;
import ua.com.pragmasoft.k1te.router.ConversationId;
import ua.com.pragmasoft.k1te.router.IConnector;
import ua.com.pragmasoft.k1te.router.IdGenerator;
import ua.com.pragmasoft.k1te.router.MessageId;
import ua.com.pragmasoft.k1te.router.Route;
import ua.com.pragmasoft.k1te.router.Router;
import ua.com.pragmasoft.k1te.router.RoutingContext;
import ua.com.pragmasoft.k1te.router.RoutingRequest;
import ua.com.pragmasoft.k1te.router.RoutingResponse;
import ua.com.pragmasoft.k1te.ws.payload.ConnectedMsg;
import ua.com.pragmasoft.k1te.ws.payload.DisconnectedMsg;
import ua.com.pragmasoft.k1te.ws.payload.ErrorMsg;
import ua.com.pragmasoft.k1te.ws.payload.KiteMsg;
import ua.com.pragmasoft.k1te.ws.payload.MsgPackKiteDecoder;
import ua.com.pragmasoft.k1te.ws.payload.MsgPackKiteEncoder;
import ua.com.pragmasoft.k1te.ws.payload.PlaintextMsg;

@ServerEndpoint(value = WsConnector.CHATS + "{chatId}", decoders = {MsgPackKiteDecoder.class},
    encoders = {MsgPackKiteEncoder.class}, subprotocols = {"v1.k1te.chat"})
@ApplicationScoped
public class WsConnector implements IConnector {

  public static final String CHATS = "/chats/";

  private static final String CLIENT_ID = "k1te.clientId";

  private static final String CONVERSATION_ID = "k1te.conversationId";

  private static final ConnectorId WS = new ConnectorId("ws");

  private final Router router;

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  private final IdGenerator idGenerator;

  @Inject
  public WsConnector(final Router router, final IdGenerator idGenerator) {
    this.router = router;
    router.register(this);
    this.idGenerator = idGenerator;
  }


  @OnOpen
  public void onOpen(Session session, @PathParam("chatId") String chatId) {
    Log.debugf("client connected to chat %s", chatId);
  }

  @OnClose
  public void onClose(Session session, @PathParam("chatId") String chatId) {
    Log.debugf("client disconnected from chat %s", chatId);
    String userId = (String) session.getUserProperties().get(CLIENT_ID);
    var removed = this.sessions.remove(userId, session);
    if (!removed) {
      Log.warnf("Session does not match when removed for user %s", userId);
    }
  }

  // TODO report with ErrorMsg
  @OnError
  public void onError(Session session, @PathParam("chatId") String chatId, Throwable t) {
    Log.errorf(t, "connection error on chat %s", chatId);
  }

  @OnMessage
  public void onMessage(KiteMsg message, @PathParam("chatId") String chatId, Session session) {
    // Decided not to use switch pattern matching until it releases from preview
    final short type = message.type();
    switch (type) {
      case KiteMsg.CONNECTED -> this.onConnected((ConnectedMsg) message, session, chatId);
      case KiteMsg.DISCONNECTED -> this.onDisconnected((DisconnectedMsg) message, session);
      case KiteMsg.PLAINTEXT -> this.onPlaintext((PlaintextMsg) message, session);
      default -> throw new IllegalStateException("Unsupported type %X".formatted(type));
    }
  }

  private void onPlaintext(PlaintextMsg message, Session session) {
    final String userId = (String) session.getUserProperties().get(CLIENT_ID);
    final ConversationId conversationId =
        (ConversationId) session.getUserProperties().get(CONVERSATION_ID);
    final var origin = new Route(this.id(), userId);
    final RoutingRequest request = new RoutingRequest(origin, conversationId, message.payload(),
        new MessageId(message.msgId()), message.timestamp());
    this.router.routeAsync(request).exceptionally(t -> {
      this.sendError(t, session);
      return null;
    });
  }


  void onConnected(ConnectedMsg m, Session session, String chatId) {
    final var userId = m.userId();
    Log.debugf("Connected %s", userId);
    this.sessions.put(userId, session);
    final var myRoute = new Route(this.id(), userId);
    final var conversation = this.router.conversation(myRoute, new ChatId(chatId));
    final var conversationId = conversation.id();
    session.getUserProperties().put(CLIENT_ID, userId);
    session.getUserProperties().put(CONVERSATION_ID, conversationId);
    final var routingRequest =
        new RoutingRequest(myRoute, conversationId, "Client joined #" + conversationId.raw());
    this.router.routeAsync(routingRequest).join();
    final var response =
        "✅ Conversation #%s has been successfully created. You can now start writing your messages to %s"
            .formatted(conversationId.raw(), chatId);
    this.sendPlaintext(response, session).exceptionally(t -> {
      this.sendError(t, session);
      return null;
    });
  }

  void onDisconnected(DisconnectedMsg m, Session session) {
    Log.debugf("Disconnected %s", m.userId());
    var removed = this.sessions.remove(m.userId(), session);
    if (!removed) {
      Log.warnf("Session does not match when removed for user %s", m.userId());
    }
    try {
      session.close();
    } catch (IOException e) {
      Log.error("Exception while closing session with user " + m.userId(), e);
    }
  }

  CompletableFuture<Void> sendPlaintext(String plaintext, Session session) {
    final var m = new PlaintextMsg(this.idGenerator.randomStringId(10), plaintext);
    return sendPlaintext(m, session);
  }

  CompletableFuture<Void> sendPlaintext(PlaintextMsg m, Session session) {
    return new CompletableFutureSendHandler(
        sendHandler -> session.getAsyncRemote().sendObject(m, sendHandler))
            .thenRun(() -> Log.debugf("Plaintext '%s'", m.payload()))
            .exceptionally((Throwable e) -> {
              Log.error(e.getMessage(), e);
              return null;
            });
  }

  CompletableFuture<Void> sendError(Throwable t, Session session) {
    Log.error("Routing error: " + t.getMessage(), t);
    return new CompletableFutureSendHandler(sendHandler -> session.getAsyncRemote()
        .sendObject(new ErrorMsg(t.getMessage(), 500), sendHandler))
            .exceptionally((Throwable e) -> {
              Log.error(e.getMessage(), e);
              return null;
            });
  }

  @Override
  public ConnectorId id() {
    return WS;
  }


  @Override
  public CompletableFuture<RoutingContext> dispatchAsync(RoutingContext routingContext) {
    var payload = routingContext.getRoutingRequest().payload();
    final String userId = routingContext.getDestination().connectorSpecificDestination();
    final MessageId messageId = routingContext.getRoutingRequest().originMessageId();
    try {
      if (payload instanceof String stringPayload) {
        Session session = Optional.ofNullable(this.sessions.get(userId))
            .orElseThrow(() -> new IllegalStateException("Client disconnected"));
        final PlaintextMsg msg = new PlaintextMsg(messageId.raw(), stringPayload);
        return sendPlaintext(msg, session)
            .thenApply(v -> routingContext.withResponse(new RoutingResponse(messageId)));
      } else {
        throw new IllegalStateException(
            "Unsupported payload type: " + payload.getClass().getName());
      }
    } catch (Exception e) {
      return CompletableFuture.failedFuture(e);
    }
  }

}

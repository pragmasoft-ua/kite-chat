package ua.com.pragmasoft.k1te.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Event;
import javax.enterprise.event.ObserverException;
import javax.inject.Inject;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import io.quarkus.logging.Log;
import ua.com.pragmasoft.k1te.ws.payload.ConnectedMsg;
import ua.com.pragmasoft.k1te.ws.payload.DisconnectedMsg;
import ua.com.pragmasoft.k1te.ws.payload.ErrorMsg;
import ua.com.pragmasoft.k1te.ws.payload.KiteMsg;
import ua.com.pragmasoft.k1te.ws.payload.MsgPackKiteDecoder;
import ua.com.pragmasoft.k1te.ws.payload.MsgPackKiteEncoder;
import ua.com.pragmasoft.k1te.ws.payload.PlaintextMsg;

@ServerEndpoint(value = KiteChatSocket.CHATS + "{channel}", decoders = {MsgPackKiteDecoder.class},
    encoders = {MsgPackKiteEncoder.class}, subprotocols = {"v1.k1te.chat"})
@ApplicationScoped
public class KiteChatSocket {

  public static final String CHATS = "/chats/";

  private static final String CLIENT_ID = "k1te.clientId";

  Map<String, Session> sessions = new ConcurrentHashMap<>();

  @Inject
  Event<PlaintextMsg> eventSource;

  @OnOpen
  public void onOpen(Session session, @PathParam("channel") String channel) {
    Log.debugf("client connected to channel %s", channel);
  }

  @OnClose
  public void onClose(Session session, @PathParam("channel") String channel) {
    Log.debugf("client disconnected from channel %s", channel);
  }

  @OnError
  public void onError(Session session, @PathParam("channel") String channel, Throwable t) {
    Log.errorf(t, "connection error on channel %s", channel);
  }

  @OnMessage
  public void onMessage(KiteMsg message, @PathParam("channel") String channel, Session session) {
    // Decided not to use switch pattern matching until it releases from preview
    final short type = message.type();
    switch (type) {
      case KiteMsg.CONNECTED -> this.onConnected((ConnectedMsg) message, session);
      case KiteMsg.DISCONNECTED -> this.onDisconnected((DisconnectedMsg) message, session);
      case KiteMsg.PLAINTEXT -> this.onPlaintext((PlaintextMsg) message, session);
      default -> throw new IllegalStateException("Unsupported type %X".formatted(type));
    }
  }

  void onConnected(ConnectedMsg m, Session session) {
    String userId = m.userId();
    Log.debugf("Connected %s", userId);
    this.sessions.put(userId, session);
    session.getUserProperties().put(CLIENT_ID, userId);
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

  void onPlaintext(PlaintextMsg m, Session session) {
    new CompletableFutureSendHandler(
        sendHandler -> session.getAsyncRemote().sendObject(m, sendHandler))
        .thenRun(() -> Log.debugf("Plaintext '%s'", m.payload())).exceptionally((Throwable e) -> {
          Log.error(e.getMessage(), e);
          return null;
        });
    notifyPlaintextMessage(m, session);
  }

  void notifyPlaintextMessage(PlaintextMsg m, Session s) {
    try {
      this.eventSource.fire(m);
    } catch (ObserverException e) {
      Log.warn(e.getLocalizedMessage(), e);
      s.getAsyncRemote().sendObject(new ErrorMsg(e.getLocalizedMessage(), 1005));
    }

  }

}

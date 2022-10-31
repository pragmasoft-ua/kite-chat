package ua.com.pragmasoft.humane.controller;

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
import ua.com.pragmasoft.humane.payload.ConnectedMsg;
import ua.com.pragmasoft.humane.payload.DisconnectedMsg;
import ua.com.pragmasoft.humane.payload.ErrorMsg;
import ua.com.pragmasoft.humane.payload.HumaneMsg;
import ua.com.pragmasoft.humane.payload.MsgPackHumaneDecoder;
import ua.com.pragmasoft.humane.payload.MsgPackHumaneEncoder;
import ua.com.pragmasoft.humane.payload.PlaintextMsg;

@ServerEndpoint(value = HumaneChatSocket.CHATS + "{channel}",
    decoders = {MsgPackHumaneDecoder.class}, encoders = {MsgPackHumaneEncoder.class},
    subprotocols = {"v1.k1te.chat"})
@ApplicationScoped
public class HumaneChatSocket {

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
  public void onMessage(HumaneMsg message, @PathParam("channel") String channel, Session session) {
    // Decided not to use switch pattern matching until it releases from preview
    final short type = message.type();
    switch (type) {
      case HumaneMsg.CONNECTED -> this.onConnected((ConnectedMsg) message, session);
      case HumaneMsg.DISCONNECTED -> this.onDisconnected((DisconnectedMsg) message, session);
      case HumaneMsg.PLAINTEXT -> this.onPlaintext((PlaintextMsg) message, session);
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
            .thenRun(() -> Log.debugf("Plaintext '%s'", m.payload()))
            .exceptionally((Throwable e) -> {
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

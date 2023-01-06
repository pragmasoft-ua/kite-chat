package ua.com.pragmasoft.k1te.ws;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import io.quarkus.logging.Log;
import ua.com.pragmasoft.k1te.router.domain.Channels;
import ua.com.pragmasoft.k1te.router.domain.Connector;
import ua.com.pragmasoft.k1te.router.domain.Id;
import ua.com.pragmasoft.k1te.router.domain.Member;
import ua.com.pragmasoft.k1te.router.domain.Router;
import ua.com.pragmasoft.k1te.router.domain.RoutingContext;
import ua.com.pragmasoft.k1te.router.domain.payload.ErrorResponse;
import ua.com.pragmasoft.k1te.router.domain.payload.JoinChannel;
import ua.com.pragmasoft.k1te.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.router.domain.payload.MessagePayload;
import ua.com.pragmasoft.k1te.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.router.domain.payload.PlaintextMessage;
import ua.com.pragmasoft.k1te.shared.KiteException;
import ua.com.pragmasoft.k1te.shared.RoutingException;

@ServerEndpoint(
    value = WsConnector.CHANNELS_PATH + "{channelName}",
    decoders = {PayloadDecoder.class},
    encoders = {PayloadEncoder.class},
    subprotocols = {"v1.k1te.chat"})
@ApplicationScoped
public class WsConnector implements Connector {

  public static final String CHANNELS_PATH = "/channels/";

  private static final String WS = "ws";

  private final Router router;

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  private final Channels channels;

  @Inject
  public WsConnector(final Router router, final Channels channels) {
    this.router = router;
    router.registerConnector(this);
    this.channels = channels;
  }

  @Override
  public String id() {
    return WS;
  }

  @OnClose
  public void onClose(Session session, @PathParam("channelName") String channelName) {
    final String connectionUri = this.connectionUriOf(session);
    Log.debugf("Member disconnected from channel %s on %s", channelName, connectionUri);
    try {
      Member client = this.channels.leaveChannel(connectionUri);
      this.router.dispatch(
          RoutingContext
              .create()
              .withOriginConnection(connectionUri)
              .withRequest(new PlaintextMessage(
                  "#%s%n✅ %s left channel %s".formatted(client.getId(), client.getUserName(),
                      client.getChannelName()))));
    } finally {
      this.sessions.remove(session.getId());
    }
  }

  @OnError
  public void onError(Session session, @PathParam("channelName") String channelName, Throwable t)
      throws IOException, EncodeException {
    Log.errorf(t, "Error on channel %s", channelName);
    if (session.isOpen()) {
      final ErrorResponse errorResponse;
      if (t instanceof KiteException ke) {
        errorResponse = new ErrorResponse("⛔" + ke.getMessage(), ke.code());
      } else {
        errorResponse = new ErrorResponse("⛔" + t.getMessage(), KiteException.SERVER_ERROR);
      }
      session.getBasicRemote().sendObject(errorResponse);
    }
  }

  @OnMessage
  public void onPayload(Payload payload, @PathParam("channelName") String channelName,
      Session session) throws IOException, EncodeException {
    if (payload instanceof JoinChannel joinCommand) {
      joinCommand.channelName = channelName;
      this.onJoinChannel(joinCommand, session);
    } else if (payload instanceof MessagePayload message) {
      this.onMessage(message, session);
    } else {
      throw new IllegalStateException(
          "Unsupported payload type %s".formatted(payload.getClass().getSimpleName()));
    }
  }

  private void onJoinChannel(JoinChannel joinChannel, Session session)
      throws IOException, EncodeException {
    Log.debugf("Join member %s to channel %s", joinChannel.memberId, joinChannel.channelName);
    try {
      String originConnection = this.connectionUriOf(session);
      Member client =
          this.channels.joinChannel(joinChannel.channelName, joinChannel.memberId, originConnection,
              joinChannel.memberName);
      var ctx = RoutingContext.create()
          .withOriginConnection(originConnection)
          .withFrom(client)
          .withRequest(new PlaintextMessage(
              "#%s%n✅ %s joined channel %s".formatted(client.getId(), client.getUserName(),
                  client.getChannelName())));
      this.router.dispatch(ctx);
      session.getBasicRemote().sendObject(
          new PlaintextMessage("✅ You joined channel %s".formatted(joinChannel.channelName)));
      this.sessions.put(session.getId(), session);
    } catch (Exception e) {
      this.onError(session, joinChannel.channelName, e);
      session.close(new CloseReason(CloseCodes.VIOLATED_POLICY, e.getMessage()));
    }
  }

  private void onMessage(MessagePayload message, Session session)
      throws IOException, EncodeException {
    Log.debugf("Message %s", message);
    var originConnection = this.connectionUriOf(session);
    var ctx = RoutingContext.create()
        .withOriginConnection(originConnection)
        .withRequest(message);
    this.router.dispatch(ctx);
    session.getBasicRemote().sendObject(ctx.response);
  }

  @Override
  public void dispatch(RoutingContext ctx) {
    var messagePayload = ctx.request;
    try {
      Session session = this.requiredSession(ctx.destinationConnection);
      session.getBasicRemote().sendObject(messagePayload);
      ctx.response = new MessageAck(messagePayload.messageId());
    } catch (IOException | EncodeException e) {
      throw new RoutingException("Communication error", e);
    }
  }

  private String connectionUriOf(Session s) {
    return this.connectionUri(Id.validate(s.getId()));
  }

  private Session requiredSession(String uri) {
    var session = this.sessions.get(this.rawConnection(uri));
    if (null == session) {
      throw new RoutingException("Web client disconnected");
    }
    return session;
  }

}

package ua.com.pragmasoft.k1te.ws;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.websocket.CloseReason;
import javax.websocket.CloseReason.CloseCodes;
import javax.websocket.EncodeException;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.PongMessage;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import io.quarkus.logging.Log;
import ua.com.pragmasoft.k1te.router.domain.Channels;
import ua.com.pragmasoft.k1te.router.domain.Connector;
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

@ServerEndpoint(value = WsConnector.CHANNELS_PATH + "{channelName}", decoders = { PayloadDecoder.class }, encoders = {
    PayloadEncoder.class }, subprotocols = { "v1.k1te.chat" })
@ApplicationScoped
public class WsConnector implements Connector {

  public static final String CHANNELS_PATH = "/channels/";

  private static final String WS = "ws";

  private final Router router;

  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  private final Channels channels;

  private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

  private static final String PING = "kite.ping";

  private static final ByteBuffer PING_BYTES = ByteBuffer.wrap(PING.getBytes(StandardCharsets.UTF_8));

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

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) {
    session.setMaxIdleTimeout(60L * 1000L);
    var handle = this.scheduler.scheduleAtFixedRate(new Pinger(session),
        30, 30, TimeUnit.SECONDS);
    session.getUserProperties().put(PING, handle);
  }

  @OnMessage
  public void onPong(Session session, PongMessage pong) {
    var data = StandardCharsets.UTF_8.decode(pong.getApplicationData()).toString();
    Log.debugf("Pong %s %s ", data, session.getId());
  }

  @OnClose
  public void onClose(Session session, @PathParam("channelName") String channelName, CloseReason closeReason) {
    final String connectionUri = this.connectionUriOf(session);
    Log.debugf("Member disconnected from channel %s on %s. Reason %s", channelName, connectionUri,
        closeReason.getCloseCode().toString());
    try {
      ScheduledFuture<?> handle = (ScheduledFuture<?>) session.getUserProperties().get(PING);
      handle.cancel(true);
      Member client = this.channels.find(connectionUri);
      this.router.dispatch(
          RoutingContext
              .create()
              .withOriginConnection(connectionUri)
              .withRequest(new PlaintextMessage(
                  "✅ %s left channel %s".formatted(client.getUserName(), client.getChannelName()))));
      this.channels.leaveChannel(connectionUri);
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
      Member client = this.channels.joinChannel(joinChannel.channelName, joinChannel.memberId, originConnection,
          joinChannel.memberName);
      var ctx = RoutingContext.create()
          .withOriginConnection(originConnection)
          .withFrom(client)
          .withRequest(new PlaintextMessage(
              "✅ %s joined channel %s".formatted(client.getUserName(), client.getChannelName())));
      this.router.dispatch(ctx);
      session.getBasicRemote().sendObject(
          new PlaintextMessage(
              "✅ You joined channel %s as %s".formatted(joinChannel.channelName, client.getUserName())));
      this.sessions.put(session.getId(), session);
    } catch (Exception e) {
      this.onError(session, joinChannel.channelName, e);
      session.close(new CloseReason(CloseCodes.VIOLATED_POLICY, "Protocol error"));
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
    return this.connectionUri(s.getId());
  }

  private Session requiredSession(String uri) {
    var session = this.sessions.get(this.rawConnection(uri));
    if (null == session) {
      throw new RoutingException("Web client disconnected");
    }
    return session;
  }

  private record Pinger(Session session) implements Runnable {

    @Override
    public void run() {
      try {
        Log.debugf("Ping %s %s ", PING, session.getId());
        session.getBasicRemote().sendPing(PING_BYTES);
      } catch (IOException io) {
        Log.debug("Ping error, close session " + session.getId(), io);
        try {
          session.close();
        } catch (IOException e) {
          // Ignore
        }
      }
    }

  }

}

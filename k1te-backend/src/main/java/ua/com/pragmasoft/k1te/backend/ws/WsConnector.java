/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.ws;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.*;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.*;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.RoutingException;
import ua.com.pragmasoft.k1te.backend.shared.TooLargeException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;

public class WsConnector implements Connector {

  public static final String SUBPROTOCOL = "k1te.chat.v1";
  public static final String WS = "ws";

  private static final Long BYTES_IN_MB = 1048576L;
  private static final Logger log = LoggerFactory.getLogger(WsConnector.class);
  private static final PayloadDecoder DECODER = new PayloadDecoder();

  private final Router router;
  private final Channels channels;
  private final Messages messages;
  private final WsConnectionRegistry connections;
  private final ObjectStore objectStore;
  private final Map<String, Integer> allowedMediaTypes =
      Map.of(
          "application/pdf", 20,
          "application/zip", 20,
          "application/x-zip-compressed", 20,
          "image/webp", 20,
          "image/gif", 20,
          "video/mp4", 20,
          "image/jpeg", 5,
          "image/png", 5);

  public WsConnector(
      final Router router,
      final Channels channels,
      Messages messages,
      final WsConnectionRegistry connections,
      ObjectStore objectStore) {
    this.router = router;
    this.messages = messages;
    router.registerConnector(this);
    this.channels = channels;
    this.connections = connections;
    this.objectStore = objectStore;
  }

  @Override
  public String id() {
    return WS;
  }

  public Payload onOpen(WsConnection connection) {
    if (log.isDebugEnabled()) {
      final var connectionUri = this.connectionUriOf(connection);
      log.debug("Member connected to channel on {}", connectionUri);
    }
    return null;
  }

  public Payload onClose(WsConnection connection) {
    final var connectionUri = this.connectionUriOf(connection);
    log.debug("Member disconnected from channel on {}", connectionUri);
    Member client = this.channels.find(connectionUri);
    this.router.dispatch(
        RoutingContext.create()
            .withOriginConnection(connectionUri)
            .withRequest(
                new PlaintextMessage(
                    "✅ %s left channel %s"
                        .formatted(client.getUserName(), client.getChannelName()))));
    this.channels.leaveChannel(connectionUri);
    return null;
  }

  public Payload onError(WsConnection connection, Throwable t) {
    if (log.isErrorEnabled()) {
      log.error("Error on connection %s".formatted(connection.connectionUri()), t);
    }
    final ErrorResponse errorResponse;
    if (t instanceof KiteException ke) {
      errorResponse = new ErrorResponse("⛔ " + ke.getMessage(), ke.code());
    } else {
      errorResponse = new ErrorResponse("⛔ " + t.getMessage(), KiteException.SERVER_ERROR);
    }
    return errorResponse;
  }

  public Payload onPayload(Payload payload, WsConnection connection) {
    if (payload instanceof UploadRequest uploadRequest) {
      return this.onUploadRequest(uploadRequest, connection);
    } else if (payload instanceof MessagePayload message) {
      return this.onMessage(message, connection);
    } else if (payload instanceof Ping) {
      return new Pong();
    } else if (payload instanceof JoinChannel joinCommand) {
      return this.onJoinChannel(joinCommand, connection);
    } else {
      throw new IllegalStateException(
          "Unsupported payload type %s".formatted(payload.getClass().getSimpleName()));
    }
  }

  private UploadResponse onUploadRequest(UploadRequest uploadRequest, WsConnection connection) {
    String fileType = uploadRequest.fileType();
    long fileSize = uploadRequest.fileSize();

    if (!allowedMediaTypes.containsKey(fileType))
      throw new ValidationException("Unsupported Media type " + fileType);
    if (allowedMediaTypes.get(fileType) * BYTES_IN_MB < fileSize) {
      long maxSize = (allowedMediaTypes.get(fileType) * BYTES_IN_MB) / 1024;
      long actualSize = fileSize / 1024;
      throw new TooLargeException(maxSize, actualSize);
    }

    final var connectionUri = this.connectionUriOf(connection);
    Member client = this.channels.find(connectionUri);
    return this.objectStore.presign(uploadRequest, client.getChannelName(), client.getId());
  }

  private Payload onJoinChannel(JoinChannel joinChannel, WsConnection connection) {
    log.debug("Join member {} to channel {}", joinChannel.memberId(), joinChannel.channelName());
    String originConnection = this.connectionUriOf(connection);
    Member client =
        this.channels.joinChannel(
            joinChannel.channelName(),
            joinChannel.memberId(),
            originConnection,
            joinChannel.memberName());

    List<HistoryMessage> historyMessages =
        this.messages.findAll(
            Messages.MessagesRequest.builder()
                .withMember(client)
                .withConnectionUri(originConnection)
                .build());

    var ctx =
        RoutingContext.create()
            .withOriginConnection(originConnection)
            .withFrom(client)
            .withRequest(
                new PlaintextMessage(
                    "✅ %s joined channel %s"
                        .formatted(client.getUserName(), client.getChannelName())));
    this.router.dispatch(ctx);

    historyMessages.forEach(
        message -> {
          Payload payload = DECODER.apply(message.getContent());
          var context =
              RoutingContext.create()
                  .withFrom(client)
                  .withTo(client)
                  .withDestinationConnection(client.getConnectionUri())
                  .withRequest((MessagePayload) payload);

          this.dispatch(context);
        });

    return new OkResponse();
  }

  private Payload onMessage(MessagePayload message, WsConnection connection) {
    log.debug("Message {}", message);

    if (message.type() == Payload.Type.TXT) {
      PlaintextMessage plaintextMessage = (PlaintextMessage) message;
      byte[] size = plaintextMessage.text().getBytes(StandardCharsets.UTF_8);
      if (size.length > 4096) throw new TooLargeException(4L, size.length / 1024L);
      message =
          new PlaintextMessage(
              plaintextMessage.text(), plaintextMessage.messageId(), plaintextMessage.created(), 2);
    }
    if (message instanceof BinaryMessage binaryMessage) {
      message =
          new BinaryMessage(
              binaryMessage.uri(),
              binaryMessage.fileName(),
              binaryMessage.fileType(),
              binaryMessage.fileSize(),
              binaryMessage.messageId(),
              binaryMessage.created(),
              2);
    }

    var originConnection = this.connectionUriOf(connection);
    var ctx = RoutingContext.create().withOriginConnection(originConnection).withRequest(message);
    this.router.dispatch(ctx);
    return ctx.response;
  }

  @Override
  public void dispatch(RoutingContext ctx) {
    var messagePayload = ctx.request;
    if (messagePayload instanceof BinaryPayload binaryPayload) {
      /*
       * Telegram sends binary url which is temporary and contains bot token so it
       * is not suitable to expose to the public web.
       * Due to this we need to copy this resource to s3 and expose s3 url instead.
       * Currently we only receive BinaryPayload from the Telegram, so there's no need
       * to check who is the sender. Later we may have other connectors though
       * Then we'll need additional flag like isTransientUrl or url may contain query
       * param ?transient=true in the BinaryPayload to copy conditionally only if flag
       * is true.
       */
      Member recipient = ctx.to;
      messagePayload =
          this.objectStore.copyTransient(
              binaryPayload, recipient.getChannelName(), recipient.getId());
    }
    WsConnection connection = this.requiredConnection(ctx.destinationConnection);
    try {
      connection.sendObject(messagePayload);
      ctx.response = new MessageAck(messagePayload.messageId());
    } catch (IOException e) {
      throw new RoutingException(e.getMessage(), e);
    }
  }

  private String connectionUriOf(WsConnection c) {
    return this.connectionUri(c.connectionUri());
  }

  private WsConnection requiredConnection(String uri) {
    var connection = this.connections.getConnection(Connector.rawConnection(uri));
    if (null == connection) {
      throw new RoutingException("Web client disconnected");
    }
    return connection;
  }

  public static interface WsConnection extends Closeable {

    public String connectionUri();

    public void sendObject(Payload payload) throws IOException;
  }

  public static interface WsConnectionRegistry {

    WsConnection getConnection(String connectionUri);
  }
}

package ua.com.pragmasoft.k1te.backend.tg;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.MessageEntity.Type;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.AbstractSendRequest;
import com.pengrad.telegrambot.request.ContentTypes;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.GetFileResponse;

import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.router.domain.RoutingContext;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryPayload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessagePayload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.PlaintextMessage;
import ua.com.pragmasoft.k1te.backend.shared.RoutingException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;

public class TelegramConnector implements Connector, Closeable {

  private static final Logger log = LoggerFactory.getLogger(TelegramConnector.class);

  private static final String UNSUPPORTED_PAYLOAD = "Unsupported payload ";
  private static final String TG = "tg";
  private static final String OK = "ok";
  private static final String HELP = """
      This bot allows to set up support channel in the current chat as a host
      or call existing support channel as a client.

      /host *channel* set up current chat as a support channel named *channel*
      /drop unregister current support channel

      /start *channel* start conversation with support channel named *channel*
      /leave leave current support channel

      *channel* name should contain only alphanumeric letters, .(dot) , -(minus), \\_(underline), ~(tilde)
      and be 8..32 characters long.

      Once conversation is established, bot will forward messages from client to host and vice versa.

      Host messages will be forwarded to the client who sent the last incoming message.

      Use ↰ (Reply To) to respond to other messages.
      """;

  private final TelegramBot bot;
  private final Router router;
  private final Channels channels;
  private final URI base;
  private final URI wsApi;

  public TelegramConnector(final TelegramBot bot, final Router router, final Channels channels,
      final URI base, URI wsApi) {
    this.bot = bot;
    this.router = router;
    this.router.registerConnector(this);
    this.channels = channels;
    this.base = base;
    if (wsApi.getScheme().equals("wss")) {
      this.wsApi = wsApi;
    } else {
      try {
        this.wsApi = new URI("wss", wsApi.getSchemeSpecificPart(), wsApi.getFragment());
      } catch (URISyntaxException e) {
        throw new IllegalStateException(e.getMessage(), e);
      }
    }
    log.info("Base {}, wsApi {}", this.base, this.wsApi);
  }

  public URI setWebhook() {
    log.debug("Register telegram webhook {}", this.base);
    var request = new SetWebhook().url(this.base.toASCIIString());
    var response = this.bot.execute(request);
    if (log.isDebugEnabled()) {
      log.debug(response.toString());
    }
    if (!response.isOk()) {
      throw new IllegalStateException(response.description());
    }
    return this.base;
  }

  public void close() {
    log.info("close");
    try {
      this.bot.execute(new DeleteWebhook());
    } finally {
      this.bot.shutdown();
    }
  }

  public String onUpdate(final Update u) {
    var message = u.message();
    var isEdited = false;
    if (null == message)
      message = u.channelPost();
    if (null == message) {
      message = u.editedMessage();
      isEdited = true;
    }
    if (null == message) {
      message = u.editedChannelPost();
    }
    if (null == message)
      return this.onUnhandledUpdate(u);
    try {
      if (isCommand(message)) {
        return this.onCommand(message);
      } else {
        return this.onMessage(message, isEdited);
      }
    } catch (Exception e) {
      log.error("onUpdate", e);
      return new SendMessage(message.chat().id(), "⛔ " + e.getMessage()).toWebhookResponse();
    }
  }

  @Override
  public String id() {
    return TG;
  }

  @Override
  public void dispatch(RoutingContext ctx) {
    Long destinationChatId = toLong(this.rawConnection(ctx.destinationConnection));
    Member from = ctx.from;
    Member to = ctx.to;
    AbstractSendRequest<?> sendMessage;
    if (ctx.request instanceof PlaintextMessage plaintext) {
      String text = plaintext.text();
      if (to.isHost()) {
        text = '#' + from.getId() + '\n' + text;
      }
      sendMessage = new SendMessage(destinationChatId, text);
    } else if (ctx.request instanceof BinaryPayload binaryPayload) {

      var fileIdOrUri = (binaryPayload instanceof TelegramBinaryMessage telegramBinaryPayload)
          ? telegramBinaryPayload.fileId()
          : binaryPayload.uri().toString();

      var binaryMessage = binaryPayload.isImage()
          ? new SendPhoto(destinationChatId, fileIdOrUri)
          : new SendDocument(destinationChatId, fileIdOrUri);

      sendMessage = binaryMessage
          .fileName(binaryPayload.fileName())
          .contentType(binaryPayload.fileType());

    } else {
      throw new RoutingException(UNSUPPORTED_PAYLOAD + ctx.request.getClass().getSimpleName());
    }
    if (log.isDebugEnabled()) {
      log.debug(">> {}", sendMessage.toWebhookResponse());
    }
    var sendResponse = this.bot.execute(sendMessage);
    if (log.isDebugEnabled()) {
      log.debug("<< {}", sendResponse);
    }
    if (!sendResponse.isOk()) {
      throw new RoutingException(
          "%s connector error: (%d) %s".formatted(this.id(), sendResponse.errorCode(), sendResponse.description()));
    }
    ctx.response = new MessageAck(ctx.request.messageId(),
        fromLong(sendResponse.message().messageId().longValue()),
        Instant.ofEpochSecond(sendResponse.message().date()));
  }

  private String onCommand(final Message message) {
    CommandWithArgs cmd = parseCommand(message);
    String command = cmd.command;
    Long rawChatId = message.chat().id();
    if ("/help".equals(command)) {
      return new SendMessage(rawChatId, HELP).parseMode(ParseMode.Markdown).toWebhookResponse();
    }
    String memberId = fromLong(rawChatId);
    String originConnection = this.connectionUri(memberId);
    String response;

    if ("/start".equals(command)) {
      String channel = cmd.args;
      String memberName = userToString(message.from());
      Member client = this.channels.joinChannel(channel, memberId, originConnection, memberName);
      var ctx = RoutingContext.create()
          .withOriginConnection(originConnection)
          .withFrom(client)
          .withRequest(new PlaintextMessage(
              "✅ %s joined channel %s".formatted(memberName, channel)));
      this.router.dispatch(ctx);
      response = "✅ You joined channel %s".formatted(channel);
    } else if ("/host".equals(command)) {
      String channelName = cmd.args;
      String title = message.chat().title();
      this.channels.hostChannel(channelName, memberId, originConnection, title);
      String channelPublicUrl = this.wsApi.toString() + "?c=" + URLEncoder.encode(channelName, StandardCharsets.UTF_8);
      response = "✅ Created channel %s. Use URL %s to configure k1te chat frontend"
          .formatted(channelName, channelPublicUrl);

    } else if ("/leave".equals(command)) {
      Member client = this.channels.leaveChannel(originConnection);
      this.router.dispatch(
          RoutingContext
              .create()
              .withOriginConnection(originConnection)
              .withFrom(client)
              .withRequest(new PlaintextMessage(
                  "✅ %s left channel %s".formatted(client.getUserName(), client.getChannelName()))));
      response = "✅ You left channel %s".formatted(client.getChannelName());

    } else if ("/drop".equals(command)) {
      Member client = this.channels.dropChannel(originConnection);
      response = "✅ You dropped channel %s".formatted(client.getChannelName());

    } else {
      throw new ValidationException("Unsupported command " + command);
    }
    return new SendMessage(rawChatId, response).toWebhookResponse();
  }

  private String onMessage(final Message message, boolean isEdited) {
    Long rawChatId = message.chat().id();
    String originConnection = this.connectionUri(fromLong(rawChatId));
    Member from = this.channels.find(originConnection);
    final String toMemberId = Optional.ofNullable(message.replyToMessage())
        .flatMap(TelegramConnector::memberIdFromHashTag)
        .or(() -> Optional.ofNullable(from.getPeerMemberId()))
        .orElseThrow(RoutingException::new);
    Member to = this.channels.find(from.getChannelName(), toMemberId);
    String msgId = fromLong(message.messageId().longValue());
    Instant messageTimestamp = Instant.ofEpochSecond(message.date());
    MessagePayload request = null;
    var document = message.document();
    if (null != document) {
      request = new TelegramBinaryMessage(
          msgId,
          document.fileId(),
          document.fileName(),
          document.mimeType(),
          document.fileSize(),
          messageTimestamp);
    } else if (null != message.photo() && message.photo().length > 0) {
      PhotoSize photo = largestPhoto(message.photo());
      var photoFileName = Optional
          .ofNullable(message.caption())
          .orElse(ContentTypes.PHOTO_FILE_NAME);
      request = new TelegramBinaryMessage(
          msgId,
          photo.fileId(),
          photoFileName,
          ContentTypes.PHOTO_MIME_TYPE,
          photo.fileSize(),
          messageTimestamp);
    } else if (null != message.text()) {
      request = new PlaintextMessage(message.text(), msgId, messageTimestamp);
    } else {
      throw new RoutingException("unsupported message type");
    }

    var ctx = RoutingContext
        .create()
        .withOriginConnection(originConnection)
        .withDestinationConnection(to.getConnectionUri())
        .withFrom(from)
        .withTo(to)
        .withRequest(request);
    this.router.dispatch(ctx);
    MessageAck ack = ctx.response;
    log.debug("Message #{} delivered", ack.messageId());
    return OK;
  }

  private String onUnhandledUpdate(final Update u) {
    log.warn("Unhandled update {}", u);
    return OK;
  }

  private static boolean isCommand(final Message message) {
    if (null == message)
      return false;
    final var entities = message.entities();
    if (null != entities && entities.length > 0) {
      final MessageEntity entity = entities[0];
      if (Type.bot_command == entity.type()) {
        return true;
      }
    }
    return false;
  }

  private static CommandWithArgs parseCommand(final Message message) {
    final var e = message.entities()[0];
    final var start = e.offset();
    final var end = e.offset() + e.length();
    final var text = message.text();
    final var command = text.substring(start, end).toLowerCase();
    final var args = text.substring(end).toLowerCase().trim();
    return new CommandWithArgs(command, args);
  }

  private static Optional<String> memberIdFromHashTag(final Message replyTo) {
    var entities = replyTo.entities();
    if (null != entities) {
      for (var e : entities) {
        if (e.type() == Type.hashtag) {
          final var hashTagString = replyTo.text().substring(e.offset() + 1, e.offset() + e.length());
          return Optional.of(hashTagString);
        }
      }
    }
    return Optional.empty();
  }

  private static String fromLong(Long raw) {
    return Long.toUnsignedString(raw, Character.MAX_RADIX);
  }

  private static Long toLong(String id) {
    return Long.parseUnsignedLong(id, Character.MAX_RADIX);
  }

  private PhotoSize largestPhoto(PhotoSize[] photos) {
    return Arrays
        .stream(photos)
        .max(Comparator.comparing(PhotoSize::fileSize))
        .orElseThrow();
  }

  private static String userToString(User user) {
    final StringBuilder b = new StringBuilder();
    var name = user.firstName();
    if (null != name && !name.isEmpty()) {
      b.append(name);
    }
    name = user.lastName();
    if (null != name && !name.isEmpty()) {
      if (!b.isEmpty()) {
        b.append(' ');
      }
      b.append(name);
    }
    return b.isEmpty() ? user.username() : b.toString();
  }

  private record CommandWithArgs(String command, String args) {
  }

  /**
   * More efficient implementation of a BinaryPayload lazily creates file url
   * which is only needed when routed to other connectors.
   * Exposes fileId which is needed to re-route the file inside the
   * Telegram connector
   *
   */
  private class TelegramBinaryMessage implements BinaryPayload {

    private final String messageId;
    private URI uri;
    private final String fileId;
    private final String fileName;
    private final String fileType;
    private final long fileSize;
    private final Instant created;

    private TelegramBinaryMessage(String messageId, String fileId, String fileName, String fileType,
        long fileSize, Instant created) {
      this.messageId = messageId;
      this.fileId = fileId;
      this.fileName = fileName;
      this.fileType = fileType;
      this.fileSize = fileSize;
      this.created = created;
    }

    @Override
    public String messageId() {
      return this.messageId;
    }

    /**
     * Lazily retrieves URI.
     */
    @Override
    public URI uri() {
      if (null == this.uri) {
        GetFile getFile = new GetFile(this.fileId());
        GetFileResponse getFileResponse = TelegramConnector.this.bot.execute(getFile);
        File file = getFileResponse.file();
        String uriString = TelegramConnector.this.bot.getFullFilePath(file);
        this.uri = URI.create(uriString);
      }
      return this.uri;
    }

    String fileId() {
      return this.fileId;
    }

    @Override
    public String fileName() {
      return this.fileName;
    }

    @Override
    public String fileType() {
      return this.fileType;
    }

    @Override
    public long fileSize() {
      return this.fileSize;
    }

    @Override
    public Instant created() {
      return this.created;
    }

  }

}

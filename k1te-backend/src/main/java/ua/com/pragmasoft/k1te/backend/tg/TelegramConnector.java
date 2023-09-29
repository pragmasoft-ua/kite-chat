package ua.com.pragmasoft.k1te.backend.tg;

import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.MessageEntity.Type;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetWebhook;

import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.router.domain.Router;
import ua.com.pragmasoft.k1te.backend.router.domain.RoutingContext;
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
    if (null == message)
      message = u.editedMessage();
    if (null == message)
      return this.onUnhandledUpdate(u);
    try {
      if (isCommand(u.message())) {
        return this.onCommand(parseCommand(message), message);
      } else if (null != u.message()) {
        return this.onOutgoingMessage(message);
      } else {
        return this.onEditedMessage(message);
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
    if (ctx.request instanceof PlaintextMessage plaintext) {
      String text = plaintext.text();
      Member from = ctx.from;
      Member to = ctx.to;
      if (to.isHost()) {
        text = '#' + from.getId() + '\n' + text;
      }
      var sendMessage = new SendMessage(destinationChatId, text);
      var sendResponse = this.bot.execute(sendMessage);
      ctx.response = new MessageAck(plaintext.messageId(),
          fromLong(sendResponse.message().messageId().longValue()),
          Instant.ofEpochSecond(sendResponse.message().date()));
    } else {
      throw new RoutingException(UNSUPPORTED_PAYLOAD + ctx.request.getClass().getSimpleName());
    }
  }

  private String onCommand(final CommandWithArgs cmd, Message msg) {
    String command = cmd.command;
    Long rawChatId = msg.chat().id();
    if ("/help".equals(command)) {
      return new SendMessage(rawChatId, HELP).parseMode(ParseMode.Markdown).toWebhookResponse();
    }
    String memberId = fromLong(rawChatId);
    String originConnection = this.connectionUri(memberId);
    String response;

    if ("/start".equals(command)) {
      String channel = cmd.args;
      String memberName = userToString(msg.from());
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
      String title = msg.chat().title();
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

  private String onOutgoingMessage(final Message msg) {
    Long rawChatId = msg.chat().id();
    String originConnection = this.connectionUri(fromLong(rawChatId));
    Member from = this.channels.find(originConnection);
    final String toMemberId = Optional.ofNullable(msg.replyToMessage())
        .flatMap(TelegramConnector::memberIdFromHashTag)
        .or(() -> Optional.ofNullable(from.getPeerMemberId()))
        .orElseThrow(RoutingException::new);
    Member to = this.channels.find(from.getChannelName(), toMemberId);
    String msgId = fromLong(msg.messageId().longValue());
    MessagePayload request = new PlaintextMessage(msg.text(), msgId, Instant.ofEpochSecond(msg.date()));
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

  private String onEditedMessage(Message message) {
    throw new UnsupportedOperationException("Message editing is currently not supported");
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

  private record CommandWithArgs(String command, String args) {
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

}

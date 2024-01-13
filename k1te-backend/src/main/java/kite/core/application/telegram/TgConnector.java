/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.ChatMember.Status;
import com.pengrad.telegrambot.model.ChatMemberUpdated;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.ContentTypes;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.MessageIdResponse;
import com.pengrad.telegrambot.response.SendResponse;
import java.io.Closeable;
import java.net.URI;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;
import kite.core.domain.MemberService;
import kite.core.domain.Route;
import kite.core.domain.RoutingProvider;
import kite.core.domain.RoutingService;
import kite.core.domain.command.Command;
import kite.core.domain.command.Command.ExecuteCommand;
import kite.core.domain.command.Command.RouteMessage;
import kite.core.domain.command.CommandExecuteCommandBuilder;
import kite.core.domain.exception.KiteException;
import kite.core.domain.exception.RoutingException;
import kite.core.domain.payload.Error;
import kite.core.domain.payload.MessagePayload;
import kite.core.domain.payload.Payload;
import kite.core.domain.payload.SendText.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TgConnector implements RoutingProvider, Closeable {

  private static final Logger log = LoggerFactory.getLogger(TgConnector.class);
  public static final Id TG = Id.of("tg");

  final TelegramBot bot;
  private final URI base;
  private final MemberService memberService;
  private final RoutingService routingService;

  public TgConnector(
      TelegramBot bot, URI base, MemberService memberService, RoutingService routingService) {
    this.bot = bot;
    this.base = base;
    this.memberService = memberService;
    this.routingService = routingService;
  }

  @Override
  public Id id() {
    return TG;
  }

  public URI registerWebhook() {
    log.info("Register telegram webhook {}", this.base);
    var request = new SetWebhook().url(this.base.toASCIIString());
    var response = this.bot.execute(request);
    log.debug(response.toString());
    if (!response.isOk()) {
      throw new IllegalStateException(response.description());
    }
    return this.base;
  }

  @Override
  public void close() {
    log.info("close");
    try {
      this.bot.execute(new DeleteWebhook());
    } finally {
      this.bot.shutdown();
    }
  }

  public String onUpdate(final Update update) {
    Optional<Command> command = this.toCommand(update);
    return command.flatMap(this::dispatchCommand).orElse("OK");
  }

  private Optional<String> dispatchCommand(Command command) {
    Optional<Payload> response = Optional.empty();
    try {
      response =
          switch (command) {
            case ExecuteCommand c -> this.memberService.executeCommand(c);
            case RouteMessage m -> this.routingService.fromRoute(m);
          };
      // TODO add communication exception which is raised when client cannot connect to its peer
      // services and is not caught here, but instead causes error response from the hook and update
      // retry.
    } catch (KiteException k) {
      response = Optional.of(Error.err(k));
    } catch (Exception e) {
      response = Optional.of(Error.err(e.getLocalizedMessage()));
    }
    Route origin = command.origin();
    return response
        .flatMap(payload -> TgUtils.toRequest(origin, payload))
        .map(BaseRequest::toWebhookResponse);
  }

  @Override
  public Optional<Payload> send(Route route, Payload message) throws KiteException {
    var request =
        TgUtils.toRequest(route, message)
            .orElseThrow(
                () ->
                    new RoutingException(
                        "Unsupported payload " + message.getClass().getSimpleName()));
    log.debug(">> {}", request.toWebhookResponse());
    var response = this.bot.execute(request);
    log.debug("<< {}", response);
    if (!response.isOk()) {
      throw new RoutingException(
          "%s connector error: (%d) %s"
              .formatted(this.id(), response.errorCode(), response.description()));
    }
    if (message instanceof MessagePayload p) {
      var ack =
          switch (response) {
            case SendResponse r ->
                new TgAckRecord(
                    fromInt(r.message().messageId()),
                    p.messageId(),
                    r.message().messageId(),
                    Instant.ofEpochSecond(r.message().date()));
            case MessageIdResponse r ->
                new TgAckRecord(
                    fromInt(r.messageId()), p.messageId(), r.messageId(), p.timestamp());
            default -> null;
          };
      return Optional.ofNullable(ack);
    } else {
      return Optional.empty();
    }
  }

  private Optional<Command> toCommand(Update update) {
    Command command = null;
    if (null != update.message()) {
      command = message(update.message(), Mode.NEW);
    } else if (null != update.channelPost()) {
      command = message(update.channelPost(), Mode.NEW);
    } else if (null != update.editedMessage()) {
      command = message(update.editedMessage(), Mode.EDITED);
    } else if (null != update.editedChannelPost()) {
      command = message(update.editedChannelPost(), Mode.EDITED);
    } else if (null != update.myChatMember()) {
      command = botStatusChanged(update.myChatMember());
    }
    return Optional.ofNullable(command);
  }

  private Command message(Message message, Mode mode) {
    var memberId = fromLong(message.from().id());
    var origin = new Route(TgConnector.TG, fromLong(message.chat().id()));
    var locale = Locale.forLanguageTag(message.from().languageCode());
    var toMember =
        Optional.ofNullable(message.replyToMessage()).flatMap(TgUtils::memberIdFromHashTag);

    if (TgUtils.startsFromCommandEntity(message) && mode.equals(Mode.NEW)) {
      return command(origin, locale, message);
    } else if (null != message.photo()) {
      return new RouteMessage(origin, memberId, locale, photo(message, mode), toMember);
    } else if (null != message.document()) {
      return new RouteMessage(origin, memberId, locale, document(message, mode), toMember);
    } else if (null != message.pinnedMessage()) {
      return pinnedMessage(origin, message.pinnedMessage());
    } else if (null != message.newChatMembers()) {
      return null;
    } else if (null != message.leftChatMember()) {
      return null;
    } else if (null != message.newChatTitle()) {
      return null;
    } else if (null != message.newChatPhoto()) {
      return null;
    } else if (null != message.deleteChatPhoto()) {
      return null;
    } else if (null != message.groupChatCreated()) {
      return null;
    } else if (null != message.channelChatCreated()) {
      return null;
    } else if (null != message.supergroupChatCreated()) {
      return null;
    } else if (null != message.mediaGroupId()) {
      return null;
    } else if (null != message.audio()) {
      return null;
    } else if (null != message.animation()) {
      return null;
    } else if (null != message.game()) {
      return null;
    } else if (null != message.sticker()) {
      return null;
    } else if (null != message.video()) {
      return null;
    } else if (null != message.videoNote()) {
      return null;
    } else if (null != message.contact()) {
      return null;
    } else if (null != message.location()) {
      return null;
    } else if (null != message.venue()) {
      return null;
    } else if (null != message.poll()) {
      return null;
    } else if (null != message.dice()) {
      return null;
    } else if (null != message.messageAutoDeleteTimerChanged()) {
      return null;
    } else if (null != message.invoice()) {
      return null;
    } else if (null != message.successfulPayment()) {
      return null;
    } else if (null != message.story()) {
      return null;
    } else if (null != message.userShared()) {
      return null;
    } else if (null != message.chatShared()) {
      return null;
    } else if (null != message.passportData()) {
      return null;
    } else if (null != message.proximityAlertTriggered()) {
      return null;
    } else if (null != message.forumTopicCreated()) {
      return null;
    } else if (null != message.forumTopicClosed()) {
      return null;
    } else if (null != message.forumTopicEdited()) {
      return null;
    } else if (null != message.forumTopicReopened()) {
      return null;
    } else if (null != message.generalForumTopicHidden()) {
      return null;
    } else if (null != message.generalForumTopicUnhidden()) {
      return null;
    } else if (null != message.writeAccessAllowed()) {
      return null;
    } else if (null != message.videoChatStarted()) {
      return null;
    } else if (null != message.videoChatScheduled()) {
      return null;
    } else if (null != message.videoChatParticipantsInvited()) {
      return null;
    } else if (null != message.videoChatEnded()) {
      return null;
    } else if (null != message.replyMarkup()) {
      return null;
    } else if (null != message.webAppData()) {
      return null;
    } else if (null != message.text() && !message.text().isEmpty()) {
      return new RouteMessage(origin, memberId, locale, text(message, mode), toMember);
    }
    return null;
  }

  private static MessagePayload text(Message message, Mode mode) {
    var origChatId = message.chat().id();
    var origMessageId = message.messageId();
    var msgId = fromInt(origMessageId);
    var timestamp = Instant.ofEpochSecond(message.date());

    return TgSendTextBuilder.builder()
        .text(message.text())
        .messageId(msgId)
        .timestamp(timestamp)
        .mode(mode)
        .origChatId(origChatId)
        .origMessageId(origMessageId)
        .build();
  }

  private MessagePayload document(Message message, Mode mode) {
    var origChatId = message.chat().id();
    var origMessageId = message.messageId();
    var msgId = fromInt(origMessageId);
    var timestamp = Instant.ofEpochSecond(message.date());
    var document = message.document();
    var caption = message.caption();

    return TgSendBinaryBuilder.builder()
        .bot(this.bot)
        .fileId(document.fileId())
        .fileName(document.fileName())
        .fileType(document.mimeType())
        .fileSize(document.fileSize())
        .text(caption)
        .messageId(msgId)
        .timestamp(timestamp)
        .mode(mode)
        .origChatId(origChatId)
        .origMessageId(origMessageId)
        .build();
  }

  private MessagePayload photo(Message message, Mode mode) {

    var origChatId = message.chat().id();
    var origMessageId = message.messageId();
    var msgId = fromInt(origMessageId);
    var timestamp = Instant.ofEpochSecond(message.date());
    var photo = TgUtils.largestPhoto(message.photo());
    var caption = message.caption();
    var fileName = Optional.ofNullable(caption).orElse(ContentTypes.PHOTO_FILE_NAME);

    return TgSendBinaryBuilder.builder()
        .bot(this.bot)
        .fileId(photo.fileId())
        .fileName(fileName)
        .fileType(ContentTypes.PHOTO_MIME_TYPE)
        .fileSize(photo.fileSize())
        .text(caption)
        .messageId(msgId)
        .timestamp(timestamp)
        .mode(mode)
        .origChatId(origChatId)
        .origMessageId(origMessageId)
        .build();
  }

  private static ExecuteCommand command(Route origin, Locale locale, Message message) {
    final var memberId = fromLong(message.from().id());
    final var memberName = TgUtils.userName(message.from());
    final var e = message.entities()[0];
    final var start = e.offset();
    final var end = start + e.length();
    var text = message.text();
    var command = text.substring(start, end).toLowerCase();
    final var at = command.indexOf("@"); // for commands like /start@bot
    if (at >= 0) {
      command = command.substring(0, at);
    }
    text = text.substring(end).trim();
    return CommandExecuteCommandBuilder.builder()
        .origin(origin)
        .locale(locale)
        .memberId(memberId)
        .memberName(memberName)
        .command(command)
        .args(text)
        .build();
  }

  private Command pinnedMessage(Route origin, Message message) {
    // Delete notification that message was pinned
    this.bot.execute(new DeleteMessage(message.chat().id(), message.messageId()));
    // We don't pass this further as a command, because we've processed it here.
    return null;
  }

  private static final EnumSet<Status> AWAY = EnumSet.of(Status.left, Status.kicked);

  // TODO It's unsafe to accidentally delete all channel data !!!
  // Need to consider better options
  // Actually now this doesn't work, because bot user is not a channel owner anymore,
  // but owner can now drop channel from any chat where bot participates
  @Deprecated
  private static Command botStatusChanged(ChatMemberUpdated myChatMember) {
    var bot = myChatMember.newChatMember();
    if (AWAY.contains(bot.status())) {
      var origin = new Route(TgConnector.TG, fromLong(myChatMember.chat().id()));
      var botUserId = fromLong(bot.user().id());
      var botName = TgUtils.userName(bot.user());
      return new ExecuteCommand(origin, Locale.getDefault(), botUserId, botName, "/drop");
    }
    return null;
  }

  private static String fromLong(Long raw) {
    return Long.toUnsignedString(raw, Character.MAX_RADIX);
  }

  private static String fromInt(Integer raw) {
    return Integer.toUnsignedString(raw, Character.MAX_RADIX);
  }
}

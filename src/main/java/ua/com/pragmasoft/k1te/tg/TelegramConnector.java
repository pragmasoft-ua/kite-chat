package ua.com.pragmasoft.k1te.tg;

import static ua.com.pragmasoft.k1te.ws.WsConnector.CHATS;
import java.io.Closeable;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.MessageEntity.Type;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import io.quarkus.logging.Log;
import ua.com.pragmasoft.k1te.router.Chat;
import ua.com.pragmasoft.k1te.router.ChatId;
import ua.com.pragmasoft.k1te.router.ConnectorId;
import ua.com.pragmasoft.k1te.router.ConversationId;
import ua.com.pragmasoft.k1te.router.IConnector;
import ua.com.pragmasoft.k1te.router.MessageId;
import ua.com.pragmasoft.k1te.router.Route;
import ua.com.pragmasoft.k1te.router.Router;
import ua.com.pragmasoft.k1te.router.RoutingContext;
import ua.com.pragmasoft.k1te.router.RoutingRequest;
import ua.com.pragmasoft.k1te.router.RoutingResponse;

public class TelegramConnector implements IConnector, Closeable {

  static sealed interface BotCommand permits HelpCommand, StartCommand, ChatCommand, UnchatCommand {
  }

  static record HelpCommand(long chatId) implements BotCommand {
  }

  private static final String CHAT_NAME_ARGUMENT_IS_REQUIRED = "chatid argument is required";

  static record StartCommand(String chatName, long chatId) implements BotCommand {

    StartCommand(String chatName, long chatId) {
      if (null == chatName || chatName.isEmpty()) {
        throw new IllegalStateException(CHAT_NAME_ARGUMENT_IS_REQUIRED);
      }
      this.chatName = chatName;
      this.chatId = chatId;
    }
  }

  static record ChatCommand(String chatName, long chatId) implements BotCommand {
    ChatCommand(String chatName, long chatId) {
      if (null == chatName || chatName.isEmpty()) {
        throw new IllegalStateException(CHAT_NAME_ARGUMENT_IS_REQUIRED);
      }
      this.chatName = chatName;
      this.chatId = chatId;
    }
  }

  static record UnchatCommand(String chatName, long chatId) implements BotCommand {
    UnchatCommand(String chatName, long chatId) {
      if (null == chatName || chatName.isEmpty()) {
        throw new IllegalStateException(CHAT_NAME_ARGUMENT_IS_REQUIRED);
      }
      this.chatName = chatName;
      this.chatId = chatId;
    }

  }

  private static final ConnectorId TG = new ConnectorId("tg");

  public static final String OK = "ok";

  static final String HELP = """
      This bot allows to set up support chat or call support chat as a client.

      /start *chatid* start conversation with support chat *chatid*
      /chat *chatid* set up current chat as a support chat *chatid*
      /unchat *chatid* unregister *chatid*

      *chatid* should contain only alphanumeric letters, .(dot) , -(minus), \\_(underline), ~(tilde)
      and be 8..32 characters long.
      """;


  static boolean isBotCommand(final Message message) {
    final var entities = message.entities();
    if (null != entities && entities.length > 0) {
      final MessageEntity entity = entities[0];
      if (Type.bot_command == entity.type()) {
        return true;
      }
    }
    return false;
  }

  static boolean isTextMessage(final Message message) {
    return message.text() != null;
  }


  static BotCommand toBotCommand(final Message message) {
    final var e = message.entities()[0];
    final var start = e.offset();
    final var end = e.offset() + e.length();
    final var text = message.text();
    final var command = text.substring(start, end).toLowerCase();
    final var args = text.substring(end).toLowerCase().trim();
    final var chatId = message.chat().id();
    return switch (command) {
      case "/help" -> new HelpCommand(chatId);
      case "/start" -> new StartCommand(args, chatId);
      case "/chat" -> new ChatCommand(args, chatId);
      case "/unchat" -> new UnchatCommand(args, chatId);
      default -> throw new IllegalStateException("Unsupported command: " + command);
    };
  }

  static <R> R logAsyncException(Throwable t) {
    Log.error(t.getLocalizedMessage(), t);
    return null;
  }

  private RoutingRequest toTextRoutingRequest(final Message message) {

    final var chatId = message.chat().id();

    final var origin = new Route(TG, Long.toString(chatId));

    final var conversationId = Optional.ofNullable(message)
    //@formatter:off
      .map(Message::replyToMessage)
      .flatMap(TelegramConnector::conversationIdFromHashTag)
      .or(() -> TelegramConnector.this.lastConversations.get(chatId))
      .orElseThrow(() -> new IllegalStateException("No active conversations. Try replying to the message with the conversation id hashtag"));
    //@formatter:on

    return new RoutingRequest(origin, conversationId, message.text(),
        MessageId.fromLong(message.messageId()), Instant.ofEpochSecond(message.date()));
  }


  private static Optional<ConversationId> conversationIdFromHashTag(final Message replyTo) {
    for (var e : replyTo.entities()) {
      if (e.type() == Type.hashtag) {
        final var hashTagString = replyTo.text().substring(e.offset() + 1, e.offset() + e.length());
        return Optional.of(new ConversationId(hashTagString));
      }
    }
    return Optional.empty();
  }

  private final TelegramBot bot;
  private final Router router;
  private final URI base;
  private final LastConversations lastConversations;

  /**
   *
   */
  public TelegramConnector(final TelegramBot bot, final Router router, final URI base,
      final LastConversations lastConversations) {
    this.bot = bot;
    this.router = router;
    this.router.register(this);
    try {
      this.base = new URI("wss", base.getSchemeSpecificPart(), base.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalArgumentException(e.getLocalizedMessage(), e);
    }
    this.lastConversations = lastConversations;
  }

  public void setWebhook(String webhookUrl) {
    Log.infof("Register telegram webhook %s", webhookUrl);
    var request = new SetWebhook().url(webhookUrl);
    var response = this.bot.execute(request);
    Log.debug(response);
    if (!response.isOk()) {
      throw new IllegalStateException(response.description());
    }
  }

  public void close() {
    Log.info("close");
    this.sendToTelegramAsync(new DeleteWebhook()).thenRun(this.bot::shutdown)
        .exceptionally(TelegramConnector::logAsyncException);
  }

  public String onUpdate(final Update u) {
    var message = u.message();
    if (message == null)
      message = u.editedMessage();
    if (message == null) {
      return this.onUnhandledUpdate(u);
    }
    try {
      if (isBotCommand(message)) {
        return this.onBotCommand(toBotCommand(message));
      } else if (isTextMessage(message)) {
        return this.onTextMessage(toTextRoutingRequest(message));
      } else {
        return this.onUnhandledUpdate(u);
      }
    } catch (Exception e) {
      return new SendMessage(message.chat().id(), "⛔ " + e.getMessage()).toWebhookResponse();
    }
  }

  @Override
  public ConnectorId id() {
    return TG;
  }

  @Override
  public CompletableFuture<RoutingContext> dispatchAsync(RoutingContext routingContext) {
    final var payload = routingContext.getRoutingRequest().payload();
    final var chatId = Long.valueOf(routingContext.getDestination().connectorSpecificDestination());
    final var conversationId = routingContext.getConversation().id();
    this.lastConversations.set(chatId, conversationId);
    if (payload instanceof String stringPayload) {
      var text = '#' + conversationId.raw() + '\n' + stringPayload;
      var sendMessage = new SendMessage(chatId, text);
      return sendToTelegramAsync(sendMessage).thenApply(SendResponse::message)
          .thenApply(Message::messageId).thenApply(MessageId::fromLong)
          .thenApply(RoutingResponse::new).thenApply(routingContext::withResponse);
    }
    return CompletableFuture.failedFuture(
        new IllegalStateException("Unsupported payload type " + payload.getClass().getName()));
  }

  protected String onUnhandledUpdate(final Update u) {
    Log.warn("Unhandled update: " + u);
    return OK;
  }

  String onBotCommand(final BotCommand command) {
    if (command instanceof HelpCommand c) {
      return this.onHelp(c);
    } else if (command instanceof final StartCommand c) {
      return this.onStart(c);
    } else if (command instanceof final ChatCommand c) {
      return this.onCreateChat(c);
    } else if (command instanceof UnchatCommand c) {
      return this.onDeleteChat(c);
    } else {
      throw new IllegalStateException("Unsupported command: " + command);
    }
  }

  String onHelp(final HelpCommand c) {
    var msg = new SendMessage(c.chatId, HELP).parseMode(ParseMode.Markdown);
    return msg.toWebhookResponse();
  }

  String onStart(final StartCommand c) {
    Log.debug(c);
    var myRoute = new Route(this.id(), Long.toString(c.chatId()));
    var conversation = this.router.conversation(myRoute, new ChatId(c.chatName));
    this.onTextMessage(new RoutingRequest(myRoute, conversation.id(),
        "Client joined #" + conversation.id().raw()));
    this.lastConversations.set(c.chatId(), conversation.id());

    return new SendMessage(c.chatId,
        "✅ Conversation #%s has been successfully created. You can now start writing your messages to %s"
            .formatted(conversation.id().raw(), conversation.chat().raw())).toWebhookResponse();
  }

  String onCreateChat(final ChatCommand c) {
    Log.debug(c);
    final Chat chat =
        new Chat(new ChatId(c.chatName), new Route(this.id(), Long.toString(c.chatId())));
    this.router.createChat(chat);
    String chatPublicUrl = this.base.resolve(CHATS + c.chatName).toASCIIString();
    return new SendMessage(c.chatId,
        "✅ Created chat %s. Use this URL to configure webchat frontend.".formatted(chatPublicUrl))
            .toWebhookResponse();
  }

  String onDeleteChat(final UnchatCommand c) {
    Log.debug(c);
    var deleted = this.router.deleteChat(new ChatId(c.chatName));
    if (null == deleted) {
      throw new IllegalArgumentException("Unknown chat " + c.chatName);
    }
    return new SendMessage(c.chatId, "✅ Deleted chat " + c.chatName).toWebhookResponse();
  }

  private String onTextMessage(final RoutingRequest request) {
    Log.debug(request);
    this.router.routeAsync(request).join();
    return OK;
  }

  private <T extends BaseRequest<T, R>, R extends BaseResponse> CompletableFuture<R> sendToTelegramAsync(
      T request) {
    return new CompletableFutureCallback<T, R>(callback -> this.bot.execute(request, callback));
  }

}

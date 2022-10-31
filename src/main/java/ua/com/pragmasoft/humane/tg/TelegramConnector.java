package ua.com.pragmasoft.humane.tg;

import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import javax.enterprise.event.Observes;
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
import io.quarkus.runtime.ShutdownEvent;
import ua.com.pragmasoft.humane.controller.HumaneChatSocket;
import ua.com.pragmasoft.k1te.chat.Chat;
import ua.com.pragmasoft.k1te.chat.ChatId;
import ua.com.pragmasoft.k1te.chat.Connector;
import ua.com.pragmasoft.k1te.chat.ConnectorId;
import ua.com.pragmasoft.k1te.chat.Conversation;
import ua.com.pragmasoft.k1te.chat.ConversationId;
import ua.com.pragmasoft.k1te.chat.MessageId;
import ua.com.pragmasoft.k1te.chat.MessageResponse;
import ua.com.pragmasoft.k1te.chat.Request;
import ua.com.pragmasoft.k1te.chat.Response;
import ua.com.pragmasoft.k1te.chat.Route;
import ua.com.pragmasoft.k1te.chat.Router;
import ua.com.pragmasoft.k1te.chat.TextMessageRequest;
import ua.com.pragmasoft.k1te.chat.TextMessageRequest.Entity;
import ua.com.pragmasoft.k1te.chat.TextMessageRequest.Entity.Kind;

public class TelegramConnector implements Connector {

  public static sealed interface Command permits HelpCommand, StartCommand, ChatCommand, UnchatCommand {
  }

  public static record HelpCommand(long chatId) implements Command {
  }

  public static record StartCommand(String chatName, long chatId) implements Command {
  }

  public static record ChatCommand(String chatName, long chatId) implements Command {
  }

  public static record UnchatCommand(String chatName, long chatId) implements Command {
  }

  private static final ConnectorId TG = new ConnectorId("tg");

  public static final String OK = "ok";

  static final String HELP = """
      This bot allows to set up support chat or call support chat as a client.

        /start *<chatid>* start conversation with support chat *chatid*
        /chat *<chatid>* set up current chat as a support chat *chatid*
        /unchat *<chatid>* unregister *<chatid>*

        *<chatid>* should contain only alphanumeric letters, '.' '-' '_' '~'
        and be 8-32 chars long
        """;

  static final Map<Type, Kind> entityMap = new EnumMap<>(
  //@formatter:off
    Map.ofEntries(
      Map.entry(Type.hashtag, Kind.HASHTAG),
      Map.entry(Type.url, Kind.URL), 
      Map.entry(Type.email, Kind.EMAIL),
      Map.entry(Type.phone_number, Kind.PHONE_NUMBER), 
      Map.entry(Type.bold, Kind.BOLD),
      Map.entry(Type.italic, Kind.ITALIC), 
      Map.entry(Type.code, Kind.CODE),
      Map.entry(Type.pre, Kind.PRE), 
      Map.entry(Type.underline, Kind.UNDERLINE),
      Map.entry(Type.strikethrough, Kind.STRIKETHROUGH)
    )
  );
  //@formatter:on

  static final Map<Kind, Type> reverseEntityMap = new EnumMap<>(
  //@formatter:off
    Map.ofEntries(
      Map.entry(Kind.HASHTAG, Type.hashtag),
      Map.entry(Kind.URL, Type.url), 
      Map.entry(Kind.EMAIL, Type.email),
      Map.entry(Kind.PHONE_NUMBER, Type.phone_number), 
      Map.entry(Kind.BOLD, Type.bold),
      Map.entry(Kind.ITALIC, Type.italic), 
      Map.entry(Kind.CODE, Type.code),
      Map.entry(Kind.PRE, Type.pre), 
      Map.entry(Kind.UNDERLINE, Type.underline),
      Map.entry(Kind.STRIKETHROUGH, Type.strikethrough)
    )
  );
  //@formatter:on

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


  static Command toBotCommand(final Message message) {
    final var e = message.entities()[0];
    final var start = e.offset();
    final var end = e.offset() + e.length();
    final var text = message.text();
    final var command = text.substring(start, end).toLowerCase();
    final var args = text.substring(end).trim();
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

  private static Entity[] toEntities(MessageEntity[] entities) {
    if (null == entities) {
      return null;
    } else if (0 == entities.length) {
      return new Entity[0];
    }
    return Arrays.stream(entities)
        .map(e -> new Entity(entityMap.get(e.type()), e.offset(), e.offset() + e.length()))
        .filter(e -> null == e.kind()).toArray(Entity[]::new);
  }

  private static MessageEntity[] toMessageEntities(Entity[] entities) {
    if (null == entities) {
      return null;
    } else if (0 == entities.length) {
      return new MessageEntity[0];
    }
    return Arrays.stream(entities)
        .map(e -> new MessageEntity(reverseEntityMap.get(e.kind()), e.start(), e.end() - e.start()))
        .toArray(MessageEntity[]::new);
  }

  private static TextMessageRequest toTextMessageRequest(final Message message) {
    if (null == message)
      return null;
    return new TextMessageRequest(MessageId.fromLong(message.messageId()),
        new Route(TG, Long.toString(message.chat().id())), Instant.ofEpochSecond(message.date()),
        message.text(), toEntities(message.entities()),
        conversationIdFromHashTag(message.replyToMessage()));
  }


  private static SendMessage toSendMessage(final TextMessageRequest message,
      final Route destination, Conversation conversation) {
    var chatId = Long.valueOf(destination.connectorSpecificDestination());
    var conversationId = conversation.id().raw();
    var text = '#' + conversationId + '\n' + message.text();
    SendMessage result = new SendMessage(chatId, text);
    var entities = message.entities();
    if (null != entities && entities.length > 0) {
      var newEntities = new Entity[entities.length + 1];
      newEntities[0] = new Entity(Kind.HASHTAG, 0, conversationId.length() + 1);
      System.arraycopy(entities, 0, newEntities, 1, entities.length);
      result.entities(toMessageEntities(newEntities));
    } else {
      result.parseMode(ParseMode.MarkdownV2);
    }
    return result;
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

  final TelegramBot bot;

  final Router router;

  final URI base;

  /**
   *
   */
  public TelegramConnector(final TelegramBot bot, final Router router, URI base) {
    this.bot = bot;
    this.router = router;
    this.router.register(this);
    this.base = base;
  }

  public void setWebhook(String webhookUrl) {
    Log.infof("Register telegram webhook %s", webhookUrl);
    var request = new SetWebhook().url(webhookUrl);
    var response = this.bot.execute(request);
    if (!response.isOk()) {
      throw new IllegalStateException(response.description());
    }
  }

  public void close(@Observes ShutdownEvent ev) {
    Log.info("close");
    this.executeAsync(new DeleteWebhook()).thenRun(this.bot::shutdown)
        .exceptionally(TelegramConnector::logAsyncException);
  }

  public String onUpdate(final Update u) {
    var message = u.message();
    if (message == null)
      message = u.editedMessage();
    if (isBotCommand(message)) {
      return this.onBotCommand(toBotCommand(message));
    } else if (isTextMessage(message)) {
      return this.onTextMessage(toTextMessageRequest(message));
    } else {
      return this.onUnhandledUpdate(u);
    }
  }

  @Override
  public ConnectorId id() {
    return TG;
  }

  @Override
  public <T extends Request<T, R>, R extends Response> CompletableFuture<R> sendAsync(T request,
      Route destination, Conversation conversation) {
    if (request instanceof TextMessageRequest tm) {
      CompletableFuture<MessageResponse> r =
          executeAsync(toSendMessage(tm, destination, conversation))
              .thenApply(SendResponse::message)
              .thenApply((Message m) -> new MessageResponse(MessageId.fromLong(m.messageId()),
                  request.id(), destination, Instant.ofEpochSecond(m.date()),
                  Optional.of(conversation.id())));
      return (CompletableFuture<R>) r;
    }
    return CompletableFuture.failedFuture(new IllegalStateException("Unsupported type"));
  }

  protected String onUnhandledUpdate(final Update u) {
    Log.warn("Unhandled update: " + u);
    return OK;
  }

  String onBotCommand(final Command command) {
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
    try {
      var conversation = this.router.findOrCreateConversation(
          new Route(this.id(), Long.toString(c.chatId())), new ChatId(c.chatName));
      return new SendMessage(c.chatId,
          "✅ Conversation #%s has been successfully created. You can now start writing your messages to %s"
              .formatted(conversation.id().raw(), conversation.chat().raw())).toWebhookResponse();
    } catch (Exception e) {
      return new SendMessage(c.chatId, "🛑" + e.getMessage()).toWebhookResponse();
    }
  }

  String onCreateChat(final ChatCommand c) {
    Log.debug(c);
    try {
      final Chat chat =
          new Chat(new ChatId(c.chatName), new Route(this.id(), Long.toString(c.chatId())));
      this.router.createChat(chat);
      String chatPublicUrl = this.base.resolve(HumaneChatSocket.CHATS + c.chatName).toASCIIString();
      return new SendMessage(c.chatId,
          "✅Created chat %s. Use this URL to configure webchat frontend.".formatted(chatPublicUrl))
              .toWebhookResponse();
    } catch (Exception e) {
      return new SendMessage(c.chatId, "🛑" + e.getMessage()).toWebhookResponse();
    }
  }

  String onDeleteChat(final UnchatCommand c) {
    Log.debug(c);
    try {
      String chatPublicUrl = this.base.resolve(HumaneChatSocket.CHATS + c.chatName).toASCIIString();
      var deleted = this.router.deleteChat(new ChatId(c.chatName));
      if (null == deleted) {
        throw new IllegalArgumentException("Unknown chat " + c.chatName);
      }
      return new SendMessage(c.chatId, "✅Deleted chat %s.".formatted(chatPublicUrl))
          .toWebhookResponse();
    } catch (Exception e) {
      return new SendMessage(c.chatId, "🛑" + e.getMessage()).toWebhookResponse();
    }
  }

  String onTextMessage(final TextMessageRequest m) {
    Log.debug(m);
    var result = this.router.sendMessageAsync(m);
    // TODO https://quarkus.io/guides/resteasy-reactive#asyncreactive-support
    result.join();
    return OK;
  }

  @SuppressWarnings("java:S2293") // this way it's less verbose
  private <T extends BaseRequest<T, R>, R extends BaseResponse> CompletableFuture<R> executeAsync(
      T request) {
    return new CompletableFutureCallback<T, R>(callback -> this.bot.execute(request, callback));
  }

}

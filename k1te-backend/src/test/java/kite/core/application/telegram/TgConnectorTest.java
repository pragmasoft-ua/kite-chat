/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.application.telegram;

import static kite.core.domain.payload.SendText.Mode.*;
import static kite.core.domain.payload.SendText.Mode.NEW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.pengrad.telegrambot.BotUtils;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.DeleteMessage;
import com.pengrad.telegrambot.request.DeleteWebhook;
import com.pengrad.telegrambot.request.SetWebhook;
import com.pengrad.telegrambot.response.BaseResponse;
import com.pengrad.telegrambot.response.SendResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import kite.core.domain.MemberService;
import kite.core.domain.RoutingProvider;
import kite.core.domain.RoutingService;
import kite.core.domain.command.Command.ExecuteCommand;
import kite.core.domain.command.Command.RouteMessage;
import kite.core.domain.exception.NotFoundException;
import kite.core.domain.exception.RoutingException;
import kite.core.domain.payload.MessagePayload;
import kite.core.domain.payload.SendText.Mode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TgConnectorTest extends TelegramBaseTest {

  private static final String OK = "OK";
  private static final Locale EN_LOCALE = Locale.forLanguageTag("en");
  private static final String UPDATES_PATH = "telegram-updates";
  private static final String COMMANDS_PATH = "commands";
  private static final String MESSAGES_PATH = "messages";

  @Mock private TelegramBot telegramBot;
  @Mock private MemberService memberService;
  @Mock private RoutingService routingService;
  @Spy private URI base = BASE_URI;
  @InjectMocks private TgConnector tgConnector;
  @Captor private ArgumentCaptor<ExecuteCommand> commandCaptor;
  @Captor private ArgumentCaptor<RouteMessage> routeMessageCaptor;

  private final BaseResponse tgResponse = Mockito.mock(BaseResponse.class);
  private final Update update = Mockito.mock(Update.class);

  @Test
  @DisplayName("Should return tg on id()")
  void should_return_tg_on_id_method() {
    RoutingProvider.Id id = tgConnector.id();

    assertThat(id).isEqualTo(TgConnector.TG);
  }

  @Test
  @DisplayName("Should register tg webhook")
  void should_register_webhook() {
    doReturn(tgResponse).when(telegramBot).execute(any(SetWebhook.class));
    doReturn(true).when(tgResponse).isOk();

    var response = tgConnector.registerWebhook();

    assertThat(response).isSameAs(base);
    verify(telegramBot, times(1)).execute(any(SetWebhook.class));
  }

  @Test
  @DisplayName("Should throw IllegalStateException on error response in registerWebhook()")
  void should_throw_illegal_state_exception_on_registerWebhook() {
    doReturn(tgResponse).when(telegramBot).execute(any(SetWebhook.class));
    doReturn(false).when(tgResponse).isOk();
    doReturn("Failed").when(tgResponse).description();

    var illegalStateException =
        assertThrows(IllegalStateException.class, () -> tgConnector.registerWebhook());

    assertThat(illegalStateException).hasMessage("Failed");
    verify(telegramBot, times(1)).execute(any(SetWebhook.class));
  }

  @Test
  @DisplayName("Should send DeleteWebhook request and shutdown bot on close()")
  void should_send_deleteWebhook_request_and_shutdown_bot() {
    doReturn(null).when(telegramBot).execute(any(DeleteWebhook.class));

    this.tgConnector.close();

    verify(telegramBot, times(1)).execute(any(DeleteWebhook.class));
    verify(telegramBot, times(1)).shutdown();
  }

  @Test
  @DisplayName("Should return OK onUpdate() if command is empty")
  void should_return_ok_onUpdate_if_command_is_empty() {
    doReturn(null).when(update).message();
    doReturn(null).when(update).channelPost();
    doReturn(null).when(update).editedMessage();
    doReturn(null).when(update).editedChannelPost();
    doReturn(null).when(update).myChatMember();

    String response = this.tgConnector.onUpdate(update);

    assertEquals(OK, response);
  }

  public static Stream<Arguments> commandSource() {
    return Stream.of(
        Arguments.of("host", "/host", "k1te_test"),
        Arguments.of("info", "/info", ""),
        Arguments.of("drop", "/drop", ""),
        Arguments.of("join", "/join", "k1te_test"),
        Arguments.of("leave", "/leave", ""),
        Arguments.of("start", "/start", ""),
        Arguments.of("start-deep-link", "/start", "join__pragmasoft__8ZlAlWeVaf"),
        Arguments.of("startgroup-deep-link", "/start", "join__pragmasoft__8ZlAlWeVaf"));
  }

  @ParameterizedTest(
      name = "Should parse {argumentsWithNames} to ExecuteCommand and invoke executeCommand()")
  @DisplayName(
      "Should parse different command Updates to ExecuteCommand and invoke executeCommand()")
  @MethodSource("commandSource")
  void should_parse_update_to_execute_command_and_invoke_executeCommand(
      String path, String command, String args) {
    doReturn(Optional.empty()).when(memberService).executeCommand(any(ExecuteCommand.class));

    String response = this.tgConnector.onUpdate(getUpdate(COMMANDS_PATH + "/" + path));

    assertEquals(OK, response);
    verify(memberService, times(1)).executeCommand(commandCaptor.capture());
    assertThat(commandCaptor.getValue())
        .hasFieldOrPropertyWithValue("memberId", FROM_ID)
        .hasFieldOrPropertyWithValue("origin", CHAT_ID)
        .hasFieldOrPropertyWithValue("command", command)
        .hasFieldOrPropertyWithValue("args", args)
        .hasFieldOrPropertyWithValue("locale", EN_LOCALE);
  }

  public static Stream<Arguments> textMessageSource() {
    return Stream.of(
        Arguments.of("txt-msg", "hello!", NEW), Arguments.of("txt-msg-edit", "Hello!", EDITED));
  }

  @ParameterizedTest(
      name = "Parse {argumentsWithNames} to RouteMessage and invoke fromRoute() method")
  @DisplayName(
      "Should parse different text message Updates to RouteMessage with proper mode and invoke"
          + " fromRoute()")
  @MethodSource("textMessageSource")
  void should_parse_text_update_to_route_message_and_invoke_fromRoute(
      String path, String text, Mode mode) {
    doReturn(Optional.empty()).when(routingService).fromRoute(any(RouteMessage.class));

    String response = this.tgConnector.onUpdate(getUpdate(MESSAGES_PATH + "/" + path));

    assertEquals(OK, response);
    verify(routingService, times(1)).fromRoute(routeMessageCaptor.capture());
    assertThat(routeMessageCaptor.getValue())
        .hasFieldOrPropertyWithValue("memberId", FROM_ID)
        .hasFieldOrPropertyWithValue("origin", CHAT_ID)
        .hasFieldOrPropertyWithValue("locale", EN_LOCALE)
        .extracting("payload", type(TgSendText.class))
        .hasFieldOrPropertyWithValue("text", text)
        .hasFieldOrPropertyWithValue("mode", mode)
        .hasFieldOrPropertyWithValue("messageId", MESSAGE_ID)
        .hasFieldOrPropertyWithValue("origChatId", ORIG_CHAT_ID)
        .hasFieldOrPropertyWithValue("origMessageId", ORIGIN_MESSAGE_ID);
  }

  public static Stream<Arguments> binaryMessageSource() {
    return Stream.of(
        Arguments.of("doc-msg", DOCUMENT_NAME, DOCUMENT_TYPE, DOCUMENT_SIZE, NEW, null),
        Arguments.of("doc-msg-edit", "file.pdf", "application/pdf", 9114637L, EDITED, null),
        Arguments.of("doc-caption", DOCUMENT_NAME, DOCUMENT_TYPE, DOCUMENT_SIZE, NEW, TEXT),
        Arguments.of("photo-msg", IMAGE_NAME, IMAGE_TYPE, IMAGE_SIZE, NEW, null),
        Arguments.of("photo-msg-edit", IMAGE_NAME, IMAGE_TYPE, 98686L, EDITED, null),
        Arguments.of("photo-caption", IMAGE_NAME, IMAGE_TYPE, IMAGE_SIZE, NEW, TEXT));
  }

  @ParameterizedTest
  @DisplayName(
      "Should parse different binary Updates to RouteMessage with a proper mode and invoke"
          + " fromRoute()")
  @MethodSource("binaryMessageSource")
  void should_parse_binary_update_to_route_message_and_invoke_fromRoute_method(
      String path, String fileName, String fileType, Long size, Mode mode, String caption) {
    doReturn(Optional.empty()).when(routingService).fromRoute(any(RouteMessage.class));

    String response = this.tgConnector.onUpdate(getUpdate(MESSAGES_PATH + "/" + path));

    assertEquals(OK, response);
    verify(routingService, times(1)).fromRoute(routeMessageCaptor.capture());
    assertThat(routeMessageCaptor.getValue())
        .hasFieldOrPropertyWithValue("memberId", FROM_ID)
        .hasFieldOrPropertyWithValue("origin", CHAT_ID)
        .hasFieldOrPropertyWithValue("locale", EN_LOCALE)
        .extracting("payload", type(TgSendBinary.class))
        .hasFieldOrPropertyWithValue("fileName", fileName)
        .hasFieldOrPropertyWithValue("fileType", fileType)
        .hasFieldOrPropertyWithValue("fileSize", size)
        .hasFieldOrPropertyWithValue("mode", mode)
        .hasFieldOrPropertyWithValue("text", caption)
        .hasFieldOrPropertyWithValue("origChatId", ORIG_CHAT_ID)
        .hasFieldOrPropertyWithValue("origMessageId", ORIGIN_MESSAGE_ID);
  }

  @Test
  @DisplayName("Should return drop ExecuteCommand on BotKicked Update")
  void should_return_drop_execute_command_on_bot_kicked_update() {
    doReturn(Optional.empty()).when(memberService).executeCommand(any(ExecuteCommand.class));

    String response = this.tgConnector.onUpdate(getUpdate("bot-kicked-from-channel"));

    assertThat(response).isEqualTo(OK);
    verify(memberService, times(1)).executeCommand(commandCaptor.capture());
    assertThat(commandCaptor.getValue()).hasFieldOrPropertyWithValue("command", "/drop");
    // TODO: 18.01.2024 check origin and memberId but currently memberId is botId
  }

  public static Stream<Arguments> pinnedMessageSource() {
    return Stream.of(
        Arguments.of("doc-pinned-msg"),
        Arguments.of("photo-pinned-msg"),
        Arguments.of("txt-pinned-msg"));
  }

  @ParameterizedTest
  @DisplayName("Should delete pinnedMessage by its id on pinnedMessage Update")
  @MethodSource("pinnedMessageSource")
  void should_delete_pinned_message_on_pinned_message_update(String path) {
    String response = this.tgConnector.onUpdate(getUpdate(MESSAGES_PATH + "/" + path));

    assertThat(response).isEqualTo(OK);
    verify(telegramBot, times(1)).execute(any(DeleteMessage.class));
  }

  public static Stream<Arguments> replyMessageSource() {
    return Stream.of(
        Arguments.of("reply-txt-msg", "8ZlAlWeVaf"),
        Arguments.of("reply-doc-msg", "1Z23lWeC"),
        Arguments.of("reply-photo-msg", "8AlAlWeKell"));
  }

  @ParameterizedTest
  @DisplayName(
      "Should use hashtag as a toMemberId on repliedMessage Update and add it to RouteMessage")
  @MethodSource("replyMessageSource")
  void should_use_hashtag_as_memberId_on_reply_message_update(String path, String toMemberId) {
    doReturn(Optional.empty()).when(routingService).fromRoute(any(RouteMessage.class));

    String response = this.tgConnector.onUpdate(getUpdate(MESSAGES_PATH + "/" + path));

    assertThat(response).isEqualTo(OK);
    verify(routingService, times(1)).fromRoute(routeMessageCaptor.capture());
    assertThat(routeMessageCaptor.getValue())
        .hasFieldOrPropertyWithValue("toMember", Optional.of(toMemberId));
  }

  @Test
  @DisplayName("Should return Webhook response onUpdate() if service returns Payload")
  void should_return_webhook_response_on_update_if_service_returns_payload() {
    doReturn(Optional.of(NOTIFICATION))
        .when(memberService)
        .executeCommand(any(ExecuteCommand.class));

    String response = this.tgConnector.onUpdate(getUpdate(COMMANDS_PATH + "/" + "host"));

    assertThat(response).contains(ORIG_CHAT_ID.toString()).contains(NOTIFICATION.text());
  }

  @Test
  @DisplayName("Should catch Exception on service invocation and convert it into Error Payload")
  void should_catch_exception_on_service_invocation_and_convert_it_into_payload() {
    String message = "Channel name k1te_test is already taken";
    doThrow(new NotFoundException(message))
        .when(memberService)
        .executeCommand(any(ExecuteCommand.class));

    String response = this.tgConnector.onUpdate(getUpdate(COMMANDS_PATH + "/" + "host"));

    assertThat(response).isNotEmpty().contains(ORIG_CHAT_ID.toString()).contains(message);
  }

  @Test
  @DisplayName("Should throw RoutingException on send() if response status is not OK")
  void should_throw_routing_exception_on_send_method_if_request_not_sent() {
    String description = "Some error";
    doReturn(tgResponse).when(telegramBot).execute(any());
    doReturn(false).when(tgResponse).isOk();
    doReturn(description).when(tgResponse).description();

    RoutingException routingException =
        assertThrows(RoutingException.class, () -> this.tgConnector.send(CHAT_ID, NOTIFICATION));

    assertThat(routingException).hasMessageContaining(description);
    verify(telegramBot, times(1)).execute(any());
  }

  @Test
  @DisplayName("Should return empty response on send() if request is not MessagePayload")
  void should_return_empty_response_on_send_if_request_in_not_message_payload() {
    doReturn(tgResponse).when(telegramBot).execute(any());
    doReturn(true).when(tgResponse).isOk();

    var response = this.tgConnector.send(CHAT_ID, NOTIFICATION);

    assertThat(response).isEmpty();
    verify(telegramBot, times(1)).execute(any());
  }

  public static Stream<Arguments> messagePayloadSource() {
    return Stream.of(
        Arguments.of(TG_SEND_TEXT),
        Arguments.of(TG_SEND_BINARY),
        Arguments.of(NEW_SEND_DOCUMENT),
        Arguments.of(EDIT_SEND_TEXT),
        Arguments.of(NEW_SEND_PHOTO));
  }

  @ParameterizedTest
  @DisplayName("Should return TgAckRecord response on send() if request is MessagePayload")
  @MethodSource("messagePayloadSource")
  void should_return_tgAck_response_on_send_if_request_in_message_payload(
      MessagePayload messagePayload) {
    SendResponse tgResponse = mock(SendResponse.class);
    Message message = mock(Message.class);

    doReturn(tgResponse).when(telegramBot).execute(any());
    doReturn(true).when(tgResponse).isOk();
    doReturn(message).when(tgResponse).message();
    doReturn(ORIGIN_MESSAGE_ID).when(message).messageId();
    doReturn(TIME).when(message).date();

    var response = this.tgConnector.send(CHAT_ID, messagePayload);

    assertThat(response)
        .isPresent()
        .get()
        .asInstanceOf(type(TgAckRecord.class))
        .hasFieldOrPropertyWithValue("overrideMessageId", MESSAGE_ID)
        .hasFieldOrPropertyWithValue("messageId", MESSAGE_ID)
        .hasFieldOrPropertyWithValue("tgMessageId", ORIGIN_MESSAGE_ID)
        .hasFieldOrPropertyWithValue("timestamp", TIMESTAMP);
  }

  private static Update getUpdate(String name) {
    try (InputStream resource =
        TgConnectorTest.class
            .getClassLoader()
            .getResourceAsStream("%s/%s.json".formatted(UPDATES_PATH, name))) {
      return BotUtils.parseUpdate(new InputStreamReader(Objects.requireNonNull(resource)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.*;
import kite.core.domain.Member.Id;
import kite.core.domain.command.Command.*;
import kite.core.domain.event.ChannelEvent.ChannelCreated;
import kite.core.domain.event.ChannelEvent.ChannelDropped;
import kite.core.domain.event.ChannelEvent.ChannelUpdated;
import kite.core.domain.event.Event;
import kite.core.domain.event.MemberEvent.MemberConnected;
import kite.core.domain.event.MemberEvent.MemberCreated;
import kite.core.domain.event.MemberEvent.MemberDeleted;
import kite.core.domain.exception.ConflictException;
import kite.core.domain.exception.NotFoundException;
import kite.core.domain.exception.ValidationException;
import kite.core.domain.payload.Notification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest extends BaseServiceTest {

  private static final Info INFO = defaultInfoCommand();
  private static final HostChannel HOST = defaultHostCommand();
  private static final Join JOIN = defaultJoinCommand();
  private static final Leave LEAVE = defaultLeaveCommand();
  private static final DropChannel DROP = defaultDropCommand();

  private static final String HOST_INFO = hostInfo();
  private static final String CHANNEL_INFO = channelInfo();
  private static final String MEMBER_INFO = memberInfo();
  private static final String ANONYMOUS_INFO = anonymousInfo();

  @Mock private Event.Publisher eventPublisher;
  @Mock private Members members;
  @Mock private Channels channels;
  @Mock private Connections connections;
  @Mock private RoutingService routingService;
  @Spy private URI wsApi = URI.create("wss://k1te.chat.com");

  @InjectMocks private MemberService memberService;

  private MemberService spiedMemberService;

  @BeforeEach
  void initMockedMemberService() {
    this.spiedMemberService = Mockito.spy(memberService);
  }

  @ParameterizedTest(name = "/help should return help text in {arguments}")
  @ValueSource(strings = {"en", "ua"})
  void should_return_help_text_according_to_locale(String lang) {
    Locale locale = Locale.forLanguageTag(lang);
    String expected = ResourceBundle.getBundle("l10n", locale).getString("help");

    var helpText = this.memberService.help(locale);

    assertThat(helpText)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(expected);
  }

  @Test
  @DisplayName("Should return host info on /info")
  void should_return_host_info() {
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_RAW_ID);

    var maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(HOST_INFO);
    verify(connections, never()).get(any());
  }

  @Test
  @DisplayName("Should return anonymous info on /info")
  void should_return_anonymous_info() {
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(null).when(connections).get(MEMBER_ORIGIN);

    var maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(ANONYMOUS_INFO);
  }

  @Test
  @DisplayName("Should return member info on /info")
  void should_return_member_info() {
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(MEMBER_CONNECTION).when(connections).get(MEMBER_ORIGIN);

    var maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(MEMBER_INFO);
  }

  @Test
  @DisplayName("Should return channel info on /info")
  void should_return_channel_info() {
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(CHANNEL_CONNECTION).when(connections).get(MEMBER_ORIGIN);

    var maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(CHANNEL_INFO);
  }

  @Test
  @DisplayName("Should throw ConflictException on /host if user already has another channel")
  void should_throw_conflict_exception_on_host_command_if_host_already_has_channel() {
    String anotherChannelName = "k1te_test_channel";
    doReturn(anotherChannelName).when(channels).getChannelName(MEMBER_RAW_ID);

    ConflictException conflictException =
        assertThrows(ConflictException.class, () -> this.memberService.hostChannel(HOST));
    assertThat(conflictException).hasMessage("You cannot host more than one channel");
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Should throw ConflictException on /host if channel name is already taken")
  void should_throw_conflict_exception_on_host_command_if_channel_name_already_taken() {
    Channel takenChannel =
        ChannelBuilder.builder()
            .name(CHANNEL_NAME)
            .hostId("anotherId")
            .defaultRoute(Route.of("tg:anotherChatId"))
            .build();
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(takenChannel).when(channels).get(CHANNEL_NAME);

    ConflictException conflictException =
        assertThrows(ConflictException.class, () -> this.memberService.hostChannel(HOST));
    assertThat(conflictException).hasMessage("Channel name is already taken");
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName(
      "Should update Channel's route on /host command if user is the host and channel's route"
          + " differs from origin one")
  void should_update_channel_on_host_command_if_user_is_host_and_route_is_different() {
    Channel channel =
        ChannelBuilder.builder()
            .name(CHANNEL_NAME)
            .hostId(MEMBER_RAW_ID)
            .defaultRoute(Route.of("tg:anotherChatId"))
            .build();
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(channel).when(channels).get(CHANNEL_NAME);
    Channel updatedChannel = ChannelBuilder.from(channel).withDefaultRoute(MEMBER_ORIGIN);

    var payload = this.memberService.hostChannel(HOST);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(
            "✅ Messages for channel %s will be sent here from now on".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new ChannelUpdated(channel, updatedChannel));
  }

  @Test
  @DisplayName("Should create a new channel on /host command")
  void should_create_new_channel_on_host_command() {
    String url = "wss://k1te.chat.com";
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(null).when(channels).get(CHANNEL_NAME);
    doReturn(url).when(wsApi).toString();

    var payload = this.memberService.hostChannel(HOST);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString(
            "✅ Created channel %s. Use URL %s?c=%s to configure k1te chat frontend"
                .formatted(CHANNEL_NAME, url, CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new ChannelCreated(CHANNEL));
  }

  @Test
  @DisplayName(
      "Should throw ConflictException on /host command if user is the host of the channel and"
          + " channel's route is the same as origin one")
  void should_throw_conflict_exception_if_user_is_host_and_channel_route_is_same() {
    Channel channel =
        ChannelBuilder.builder()
            .name(CHANNEL_NAME)
            .hostId(MEMBER_RAW_ID)
            .defaultRoute(MEMBER_ORIGIN)
            .build();
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_RAW_ID);
    doReturn(channel).when(channels).get(CHANNEL_NAME);

    ConflictException conflictException =
        assertThrows(ConflictException.class, () -> this.memberService.hostChannel(HOST));

    assertThat(conflictException)
        .hasMessage(
            "You already have a channel with name %s that is bound to this chat"
                .formatted(CHANNEL_NAME));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Should reconnect member to the channel on /join command")
  void should_reconnect_member_to_channel_on_join() {
    Member member = MEMBER.withRoutes(Set.of(Route.of("ws:newRoute")));
    doReturn(member).when(members).get(MEMBER_ID);

    var payload = this.memberService.join(JOIN);

    assertThat(payload).isEmpty();
    verify(eventPublisher, times(1)).publishEvent(new MemberConnected(MEMBER_ID, MEMBER_ORIGIN));
  }

  @Test
  @DisplayName("Should throw NotFound Exception if channel does not exist on /join command")
  void should_throw_not_found_exception_on_join_if_channel_not_exist() {
    doReturn(null).when(members).get(MEMBER_ID);
    doReturn(null).when(channels).get(CHANNEL_NAME);

    NotFoundException notFoundException =
        assertThrows(NotFoundException.class, () -> this.memberService.join(JOIN));

    assertThat(notFoundException)
        .hasMessage("Channel with name %s does not exist".formatted(CHANNEL_NAME));
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Should create a new Member and send Notification to the channel on /join command")
  void should_create_new_member_and_send_notification_to_channel_on_join() {
    Notification notification =
        Notification.info("%s joined channel %s".formatted(MEMBER_NAME, CHANNEL_NAME));
    doReturn(null).when(members).get(MEMBER_ID);
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);

    var payload = this.memberService.join(JOIN);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString("✅ You joined channel %s".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new MemberCreated(MEMBER));
    verify(routingService, times(1)).sendToRoute(CHANNEL.defaultRoute(), notification);
  }

  @Test
  @DisplayName("Should create a new Member and send Notification to the channel on /joinAs command")
  void should_create_new_member_and_send_notification_to_channel_on_joinAS() {
    Notification notification =
        Notification.info("%s joined channel %s".formatted(MEMBER_NAME, CHANNEL_NAME));
    doReturn(null).when(members).get(MEMBER_ID);
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);

    var payload = this.memberService.joinAs(JOIN);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString("✅ You joined channel %s".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new MemberCreated(MEMBER));
    verify(routingService, times(1)).sendToRoute(CHANNEL.defaultRoute(), notification);
  }

  @Test
  @DisplayName("Should return a warn Notification about not being in any channel to /leave it")
  void should_return_warn_about_not_being_in_channel_on_leave_command() {
    doReturn(null).when(connections).get(MEMBER_ORIGIN);

    var payload = this.memberService.leave(LEAVE);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString("⚠️ You are not joined to any channel");
  }

  @Test
  @DisplayName("Should delete Member on /leave and send notification to the channel")
  void should_delete_member_and_send_notification_to_channel_on_leave_command() {
    Notification channelNotification =
        Notification.info("#%s left channel %s".formatted(MEMBER_ID.raw(), CHANNEL_NAME));
    doReturn(MEMBER_CONNECTION).when(connections).get(MEMBER_ORIGIN);
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);

    var payload = this.memberService.leave(LEAVE);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString("✅ You left channel %s".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new MemberDeleted(MEMBER_ID));
    verify(routingService, times(1)).sendToRoute(CHANNEL.defaultRoute(), channelNotification);
  }

  @Test
  @DisplayName("Should /drop channel")
  void should_drop_channel() {
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_RAW_ID);

    var payload = this.memberService.dropChannel(DROP);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .hasToString("✅ You dropped channel %s".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new ChannelDropped(CHANNEL_NAME));
  }

  @Test
  @DisplayName("Should throw NotFound Exception on /drop if user does not host any channel")
  void should_throw_not_found_exception_on_drop_command() {
    doReturn(null).when(channels).getChannelName(MEMBER_RAW_ID);

    NotFoundException notFoundException =
        assertThrows(NotFoundException.class, () -> this.memberService.dropChannel(DROP));

    assertThat(notFoundException).hasMessage("You don't host any channels to drop");
    verify(eventPublisher, never()).publishEvent(any());
  }

  @Test
  @DisplayName("Should invoke help() method on /help executeCommand")
  void should_invoke_help_method_on_help_execute_command() {
    var helpPayload = Optional.of(Notification.plain("leave Payload"));
    var helpCommand = this.buildExecuteCommand("/help");
    doReturn(helpPayload).when(spiedMemberService).help(DEFAULT_LOCALE);

    var payload = this.spiedMemberService.executeCommand(helpCommand);

    assertThat(payload).isSameAs(helpPayload);
    verify(spiedMemberService, times(1)).help(DEFAULT_LOCALE);
  }

  @Test
  @DisplayName("Should invoke info() method on /info executeCommand")
  void should_invoke_info_method_on_info_execute_command() {
    var infoPayload = Optional.of(Notification.plain("info Payload"));
    var infoCommand = this.buildExecuteCommand("/info");
    doReturn(infoPayload).when(spiedMemberService).info(INFO);

    var payload = this.spiedMemberService.executeCommand(infoCommand);

    assertThat(payload).isSameAs(infoPayload);
    verify(spiedMemberService, times(1)).info(INFO);
  }

  @Test
  @DisplayName("Should invoke host() method on /host executeCommand")
  void should_invoke_host_method_on_host_execute_command() {
    var hostPayload = Optional.of(Notification.plain("host Payload"));
    var hostCommand = this.buildExecuteCommand("/host", CHANNEL_NAME);
    doReturn(hostPayload).when(spiedMemberService).hostChannel(HOST);

    var payload = this.spiedMemberService.executeCommand(hostCommand);

    assertThat(payload).isSameAs(hostPayload);
    verify(spiedMemberService, times(1)).hostChannel(HOST);
  }

  @Test
  @DisplayName("Should invoke join() method on /join executeCommand")
  void should_invoke_join_method_on_join_execute_command() {
    var joinPayload = Optional.of(Notification.plain("join Payload"));
    var joinCommand = this.buildExecuteCommand("/join", CHANNEL_NAME);
    doReturn(joinPayload).when(spiedMemberService).join(JOIN);

    var payload = this.spiedMemberService.executeCommand(joinCommand);

    assertThat(payload).isSameAs(joinPayload);
    verify(spiedMemberService, times(1)).join(JOIN);
  }

  @Test
  @DisplayName("Should invoke leave() method on /leave executeCommand")
  void should_invoke_leave_method_on_leave_execute_command() {
    var leavePayload = Optional.of(Notification.plain("leave Payload"));
    var leaveCommand = this.buildExecuteCommand("/leave");
    doReturn(leavePayload).when(spiedMemberService).leave(LEAVE);

    var payload = this.spiedMemberService.executeCommand(leaveCommand);

    assertThat(payload).isSameAs(leavePayload);
    verify(spiedMemberService, times(1)).leave(LEAVE);
  }

  @Test
  @DisplayName("Should invoke drop() method on /drop executeCommand")
  void should_invoke_drop_method_on_drop_execute_command() {
    var dropPayload = Optional.of(Notification.plain("drop Payload"));
    var dropCommand = this.buildExecuteCommand("/drop");
    doReturn(dropPayload).when(spiedMemberService).dropChannel(DROP);

    var payload = this.spiedMemberService.executeCommand(dropCommand);

    assertThat(payload).isSameAs(dropPayload);
    verify(spiedMemberService, times(1)).dropChannel(DROP);
  }

  @ParameterizedTest(
      name =
          "Should invoke start() method on {arguments} executeCommand and invoke help() method"
              + " if args are empty")
  @ValueSource(strings = {"/start", "/startgroup"})
  void should_invoke_help_method_on_start_execute_command_without_args(String command) {
    var helpPayload = Optional.of(Notification.plain("help Payload"));
    var startCommand = this.buildExecuteCommand(command);
    doReturn(helpPayload).when(spiedMemberService).help(DEFAULT_LOCALE);

    var payload = this.spiedMemberService.executeCommand(startCommand);

    assertThat(payload).isSameAs(helpPayload);
    verify(spiedMemberService, times(1)).help(DEFAULT_LOCALE);
  }

  @ParameterizedTest(
      name =
          "Should invoke start() method on {arguments} executeCommand and invoke host() method"
              + " if args are host__")
  @ValueSource(strings = {"/start", "/startgroup"})
  void should_invoke_host_method_on_start_execute_command_with_host_args(String command) {
    var hostPayload = Optional.of(Notification.plain("host Payload"));
    String args = "host__%s".formatted(CHANNEL_NAME);
    var startCommand = this.buildExecuteCommand(command, args);
    doReturn(hostPayload).when(spiedMemberService).hostChannel(HOST);

    var payload = this.spiedMemberService.executeCommand(startCommand);

    assertThat(payload).isSameAs(hostPayload);
    verify(spiedMemberService, times(1)).hostChannel(HOST);
  }

  @ParameterizedTest(
      name =
          "Should invoke start() method on {arguments} executeCommand and invoke joinAs() method"
              + " if args are join__channel__id")
  @ValueSource(strings = {"/start", "/startgroup"})
  void should_invoke_joinAs_method_on_start_execute_command_with_joinAs_args(String command) {
    var joinAsPayload = Optional.of(Notification.plain("joinAs Payload"));
    String args = "join__%s__%s".formatted(CHANNEL_NAME, MEMBER_RAW_ID);
    var startCommand = this.buildExecuteCommand(command, args);
    doReturn(joinAsPayload).when(spiedMemberService).joinAs(JOIN);

    var payload = this.spiedMemberService.executeCommand(startCommand);

    assertThat(payload).isSameAs(joinAsPayload);
    verify(spiedMemberService, times(1)).joinAs(JOIN);
  }

  @ParameterizedTest(
      name =
          "Should invoke start() method on {arguments} executeCommand and invoke join() method"
              + " if args are join__channel")
  @ValueSource(strings = {"/start", "/startgroup"})
  void should_invoke_join_method_on_start_execute_command_with_join_args(String command) {
    var joinPayload = Optional.of(Notification.plain("join Payload"));
    String args = "join__%s".formatted(CHANNEL_NAME);
    var startCommand = this.buildExecuteCommand(command, args);
    doReturn(joinPayload).when(spiedMemberService).join(JOIN);

    var payload = this.spiedMemberService.executeCommand(startCommand);

    assertThat(payload).isSameAs(joinPayload);
    verify(spiedMemberService, times(1)).join(JOIN);
  }

  @ParameterizedTest(
      name =
          "Should invoke start() method on {arguments} executeCommand and invoke join() method"
              + " if args are channelName")
  @ValueSource(strings = {"/start", "/startgroup"})
  void should_invoke_join_method_on_start_execute_command_with_channel_name_args(String command) {
    var joinPayload = Optional.of(Notification.plain("join Payload"));
    var startCommand = this.buildExecuteCommand(command, CHANNEL_NAME);
    doReturn(joinPayload).when(spiedMemberService).join(JOIN);

    var payload = this.spiedMemberService.executeCommand(startCommand);

    assertThat(payload).isSameAs(joinPayload);
    verify(spiedMemberService, times(1)).join(JOIN);
  }

  @Test
  @DisplayName("Should throw ValidationException if ExecuteCommand is not supported")
  void should_throw_validation_exception_if_execute_command_is_not_supported() {
    String command = "/1234";
    var unsupportedCommand = this.buildExecuteCommand(command);

    ValidationException validationException =
        assertThrows(
            ValidationException.class, () -> this.memberService.executeCommand(unsupportedCommand));

    assertThat(validationException).hasMessage("Unsupported command %s".formatted(command));
  }

  private static String hostInfo() {
    return "✅ You are the host of the channel " + CHANNEL_NAME;
  }

  private static String channelInfo() {
    return "✅ You can respond to messages in the channel " + CHANNEL_NAME;
  }

  private static String memberInfo() {
    return "✅ You are the member of the channel " + CHANNEL_NAME;
  }

  private static String anonymousInfo() {
    return """
      ✅ You don't talk to any channels at the moment
      To start talking use /join <channel>
      For instructions use /help
      """;
  }

  private static Info defaultInfoCommand() {
    return new Info(MEMBER_ORIGIN, MEMBER_RAW_ID, DEFAULT_LOCALE);
  }

  private static HostChannel defaultHostCommand() {
    return new HostChannel(MEMBER_ORIGIN, CHANNEL_NAME, MEMBER_RAW_ID, DEFAULT_LOCALE);
  }

  private static Join defaultJoinCommand() {
    return new Join(
        MEMBER_ORIGIN, new Id(CHANNEL_NAME, MEMBER_RAW_ID), MEMBER_NAME, DEFAULT_LOCALE);
  }

  private static Leave defaultLeaveCommand() {
    return new Leave(MEMBER_ORIGIN, DEFAULT_LOCALE);
  }

  private static DropChannel defaultDropCommand() {
    return new DropChannel(MEMBER_RAW_ID, DEFAULT_LOCALE);
  }

  private ExecuteCommand buildExecuteCommand(String command) {
    return this.buildExecuteCommand(command, "");
  }

  private ExecuteCommand buildExecuteCommand(String command, String args) {
    return new ExecuteCommand(
        MEMBER_ORIGIN, DEFAULT_LOCALE, MEMBER_RAW_ID, MEMBER_NAME, command, args);
  }
}

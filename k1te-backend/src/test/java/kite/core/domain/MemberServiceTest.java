/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.Locale;
import java.util.Optional;
import java.util.ResourceBundle;
import kite.core.domain.Connection.ChannelConnection;
import kite.core.domain.Connection.MemberConnection;
import kite.core.domain.command.Command.HostChannel;
import kite.core.domain.command.Command.Info;
import kite.core.domain.event.ChannelEvent.ChannelCreated;
import kite.core.domain.event.ChannelEvent.ChannelUpdated;
import kite.core.domain.event.Event;
import kite.core.domain.exception.ConflictException;
import kite.core.domain.payload.Notification;
import kite.core.domain.payload.Payload;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MemberServiceTest {

  private static final String CHANNEL_NAME = "k1te_test";
  private static final String MEMBER_ID = "memberId";
  private static final Route ORIGIN = Route.of("tg:originChatId");
  private static final Locale DEFAULT_LOCALE = Locale.forLanguageTag("en");

  private static final Info INFO = new Info(ORIGIN, MEMBER_ID, DEFAULT_LOCALE);
  private static final HostChannel HOST =
      new HostChannel(ORIGIN, CHANNEL_NAME, MEMBER_ID, DEFAULT_LOCALE);
  private static final String HOST_INFO = "✅ You are the host of the channel " + CHANNEL_NAME;
  private static final String CHANNEL_INFO =
      "✅ You can respond to messages in the channel " + CHANNEL_NAME;
  private static final String MEMBER_INFO = "✅ You are the member of the channel " + CHANNEL_NAME;
  private static final String ANONYMOUS_INFO =
      """
      ✅ You don't talk to any channels at the moment
      To start talking use /join <channel>
      For instructions use /help
      """;

  @Mock private Event.Publisher eventPublisher;
  @Mock private Members members;
  @Mock private Channels channels;
  @Mock private Connections connections;
  @Mock private RoutingService routingService;
  @Spy private URI wsApi = URI.create("wss://k1te.chat.com");

  @InjectMocks private MemberService memberService;

  @ParameterizedTest(name = "/help should return help text in {arguments}")
  @ValueSource(strings = {"en", "ua"})
  void should_return_help_text_according_to_locale(String lang) {
    Locale locale = Locale.forLanguageTag(lang);
    String expected = ResourceBundle.getBundle("l10n", locale).getString("help");

    Optional<Payload> helpText = this.memberService.help(locale);

    assertThat(helpText)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(expected);
  }

  @Test
  @DisplayName("Should return host info on /info")
  void should_return_host_info() {
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_ID);

    Optional<Payload> maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(HOST_INFO);
    verify(connections, never()).get(any());
  }

  @Test
  @DisplayName("Should return anonymous info on /info")
  void should_return_anonymous_info() {
    doReturn(null).when(channels).getChannelName(MEMBER_ID);
    doReturn(null).when(connections).get(ORIGIN);

    Optional<Payload> maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(ANONYMOUS_INFO);
  }

  @Test
  @DisplayName("Should return member info on /info")
  void should_return_member_info() {
    MemberConnection memberConnection =
        new MemberConnection(ORIGIN, new Member.Id(CHANNEL_NAME, MEMBER_ID));
    doReturn(null).when(channels).getChannelName(MEMBER_ID);
    doReturn(memberConnection).when(connections).get(ORIGIN);

    Optional<Payload> maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(MEMBER_INFO);
  }

  @Test
  @DisplayName("Should return channel info on /info")
  void should_return_channel_info() {
    ChannelConnection channelConnection = new ChannelConnection(ORIGIN, CHANNEL_NAME);
    doReturn(null).when(channels).getChannelName(MEMBER_ID);
    doReturn(channelConnection).when(connections).get(ORIGIN);

    Optional<Payload> maybeHostInfo = this.memberService.info(INFO);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(CHANNEL_INFO);
  }

  @Test
  @DisplayName("Should throw ConflictException on /host if user already has another channel")
  void should_throw_conflict_exception_on_host_command_if_host_already_has_channel() {
    String anotherChannelName = "k1te_test_channel";
    doReturn(anotherChannelName).when(channels).getChannelName(MEMBER_ID);

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
    doReturn(null).when(channels).getChannelName(MEMBER_ID);
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
            .hostId(MEMBER_ID)
            .defaultRoute(Route.of("tg:anotherChatId"))
            .build();
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_ID);
    doReturn(channel).when(channels).get(CHANNEL_NAME);
    Channel updatedChannel = ChannelBuilder.from(channel).withDefaultRoute(ORIGIN);

    Optional<Payload> payload = this.memberService.hostChannel(HOST);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(
            "✅ Messages for channel %s will be sent here from now on".formatted(CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new ChannelUpdated(channel, updatedChannel));
  }

  @Test
  @DisplayName("Should create a new channel on /host command")
  void should_create_new_channel_on_host_command() {
    Channel createdChannel =
        ChannelBuilder.builder().name(CHANNEL_NAME).hostId(MEMBER_ID).defaultRoute(ORIGIN).build();
    String url = "wss://k1te.chat.com";
    doReturn(null).when(channels).getChannelName(MEMBER_ID);
    doReturn(null).when(channels).get(CHANNEL_NAME);
    doReturn(url).when(wsApi).toString();

    Optional<Payload> payload = this.memberService.hostChannel(HOST);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(
            "✅ Created channel %s. Use URL %s?c=%s to configure k1te chat frontend"
                .formatted(CHANNEL_NAME, url, CHANNEL_NAME));
    verify(eventPublisher, times(1)).publishEvent(new ChannelCreated(createdChannel));
  }

  @Test
  @DisplayName(
      "Should throw ConflictException on /host command if user is the host of the channel and"
          + " channel's route is the same as origin one")
  void should_throw_conflict_exception_if_user_is_host_and_channel_route_is_same() {
    Channel channel =
        ChannelBuilder.builder().name(CHANNEL_NAME).hostId(MEMBER_ID).defaultRoute(ORIGIN).build();
    doReturn(CHANNEL_NAME).when(channels).getChannelName(MEMBER_ID);
    doReturn(channel).when(channels).get(CHANNEL_NAME);

    ConflictException conflictException =
        assertThrows(ConflictException.class, () -> this.memberService.hostChannel(HOST));

    assertThat(conflictException)
        .hasMessage(
            "You already have a channel with name %s that is bound to this chat"
                .formatted(CHANNEL_NAME));
    verify(eventPublisher, never()).publishEvent(any());
  }
}

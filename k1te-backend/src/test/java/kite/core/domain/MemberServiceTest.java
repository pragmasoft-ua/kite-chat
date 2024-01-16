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
import kite.core.domain.command.Command.Info;
import kite.core.domain.event.Event;
import kite.core.domain.payload.Notification;
import kite.core.domain.payload.Payload;
import org.assertj.core.api.InstanceOfAssertFactories;
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

    Optional<Payload> helpText = memberService.help(locale);

    assertThat(helpText)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(expected);
  }

  @Test
  void should_return_host_info() {
    String hostId = "hostId";
    Route origin = Route.of("tg:my-chat");
    Locale locale = Locale.forLanguageTag("en");
    Info info = new Info(origin, hostId, locale);
    doReturn(CHANNEL_NAME).when(channels).getChannelName(hostId);

    Optional<Payload> maybeHostInfo = memberService.info(info);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(HOST_INFO);
    verify(connections, never()).get(any());
  }

  @Test
  void should_return_anonymous_info() {
    String memberId = "memberId";
    Route origin = Route.of("tg:my-chat");
    Locale locale = Locale.forLanguageTag("en");
    Info info = new Info(origin, memberId, locale);
    doReturn(null).when(channels).getChannelName(memberId);
    doReturn(null).when(connections).get(origin);

    Optional<Payload> maybeHostInfo = memberService.info(info);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(ANONYMOUS_INFO);
  }

  @Test
  void should_return_member_info() {
    String memberId = "memberId";
    Route origin = Route.of("tg:my-chat");
    Locale locale = Locale.forLanguageTag("en");
    Info info = new Info(origin, memberId, locale);
    MemberConnection memberConnection =
        new MemberConnection(origin, new Member.Id(CHANNEL_NAME, memberId));
    doReturn(null).when(channels).getChannelName(memberId);
    doReturn(memberConnection).when(connections).get(origin);

    Optional<Payload> maybeHostInfo = memberService.info(info);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(MEMBER_INFO);
  }

  @Test
  void should_return_channel_info() {
    String memberId = "memberId";
    Route origin = Route.of("tg:my-chat");
    Locale locale = Locale.forLanguageTag("en");
    Info info = new Info(origin, memberId, locale);
    ChannelConnection channelConnection = new ChannelConnection(origin, CHANNEL_NAME);
    doReturn(null).when(channels).getChannelName(memberId);
    doReturn(channelConnection).when(connections).get(origin);

    Optional<Payload> maybeHostInfo = memberService.info(info);

    assertThat(maybeHostInfo)
        .isPresent()
        .get()
        .asInstanceOf(InstanceOfAssertFactories.type(Notification.class))
        .hasToString(CHANNEL_INFO);
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import kite.core.domain.Connection.MemberConnection;
import kite.core.domain.Member.Id;
import kite.core.domain.command.Command.RouteMessage;
import kite.core.domain.event.Event;
import kite.core.domain.event.MessageRouted;
import kite.core.domain.event.MessageRoutedBuilder;
import kite.core.domain.exception.ConflictException;
import kite.core.domain.exception.NotFoundException;
import kite.core.domain.payload.Notification;
import kite.core.domain.payload.Payload;
import kite.core.domain.payload.SendText;
import kite.core.domain.payload.SendTextRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoutingServiceTest extends KiteCoreBaseServiceTest {

  private static final SendTextRecord TEXT_PAYLOAD = textMessagePayload();

  private static final RouteMessage CHANNEL_MESSAGE = channelRouteMessage(null);
  private static final RouteMessage MEMBER_MESSAGE = memberRouteMessage();

  @Mock private Event.Publisher eventPublisher;
  @Mock private Members members;
  @Mock private Channels channels;
  @Mock private Connections connections;
  @Mock private Router router;

  @InjectMocks private RoutingService routingService;

  private RoutingService spiedRoutingService;

  @BeforeEach
  void initSpiedRoutingService() {
    this.spiedRoutingService = Mockito.spy(routingService);
  }

  @Test
  @DisplayName(
      "Should throw ConflictException on fromMember() if connection's memberId differs from"
          + " origin's one")
  void should_throw_conflict_exception_on_fromMember_method_if_memberId_not_same() {
    var anotherConnection = new MemberConnection(MEMBER_ORIGIN, new Id(CHANNEL_NAME, "anotherId"));
    var conflictException =
        assertThrows(
            ConflictException.class,
            () -> this.routingService.fromMember(anotherConnection, MEMBER_MESSAGE));

    assertThat(conflictException).hasMessage("You need to /join %s".formatted(CHANNEL_NAME));
  }

  @Test
  @DisplayName("Should throw NotFoundException on fromMember() if member not found")
  void should_throw_not_found_exception_on_fromMember_method_if_member_not_found() {
    doReturn(null).when(members).get(MEMBER_ID);

    var notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> this.routingService.fromMember(MEMBER_CONNECTION, MEMBER_MESSAGE));

    assertThat(notFoundException).hasMessage("Member not found: %s".formatted(MEMBER_ID));
  }

  @Test
  @DisplayName("Should throw NotFoundException on fromMember() if channel not found")
  void should_throw_not_found_exception_on_fromMember_method_if_channel_not_found() {
    doReturn(MEMBER).when(members).get(MEMBER_ID);
    doReturn(null).when(channels).get(CHANNEL_NAME);

    var notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> this.routingService.fromMember(MEMBER_CONNECTION, MEMBER_MESSAGE));

    assertThat(notFoundException).hasMessage("Channel not found: %s".formatted(CHANNEL_NAME));
  }

  @Test
  @DisplayName("Should send message to channel on fromMember()")
  void should_send_message_to_channel() {
    var responsePayload = Optional.of(Notification.plain("OK"));
    var messageWithTag =
        TEXT_PAYLOAD.overrideText(
            "#%s %s\n%s".formatted(MEMBER_RAW_ID, MEMBER_NAME, TEXT_PAYLOAD.text()));
    MessageRouted messageRoutedEvent =
        MessageRoutedBuilder.builder()
            .channel(CHANNEL)
            .member(MEMBER)
            .direction(Direction.TO_CHANNEL)
            .request(messageWithTag)
            .responses(Map.of(CHANNEL_ORIGIN, responsePayload.get()))
            .build();
    doReturn(MEMBER).when(members).get(MEMBER_ID);
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);
    doReturn(responsePayload).when(router).send(CHANNEL_ORIGIN, messageWithTag);

    var payload = this.routingService.fromMember(MEMBER_CONNECTION, MEMBER_MESSAGE);

    assertThat(payload).isSameAs(responsePayload);
    verify(router, times(1)).send(CHANNEL_ORIGIN, messageWithTag);
    verify(eventPublisher, times(1)).publishEvent(messageRoutedEvent);
  }

  @Test
  @DisplayName("Should throw NotFoundException on fromChannel() if channel not found")
  void should_throw_not_found_exception_on_fromChannel_if_channel_not_found() {
    doReturn(null).when(channels).get(CHANNEL_NAME);

    NotFoundException notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> this.routingService.fromChannel(CHANNEL_CONNECTION, CHANNEL_MESSAGE));

    assertThat(notFoundException).hasMessage("Channel not found: %s".formatted(CHANNEL_NAME));
  }

  @Test
  @DisplayName("Should throw NotFoundException on fromChannel() if channel does not have peerId")
  void should_throw_not_found_exception_on_fromChannel_if_no_peerId_found() {
    var channelWithoutPeer =
        new Channel(CHANNEL_NAME, HOST_ID, CHANNEL_ORIGIN, Optional.empty(), Optional.empty());
    doReturn(channelWithoutPeer).when(channels).get(CHANNEL_NAME);

    NotFoundException notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> this.routingService.fromChannel(CHANNEL_CONNECTION, CHANNEL_MESSAGE));

    assertThat(notFoundException).hasMessage("Unknown peer");
  }

  @Test
  @DisplayName("Should throw NotFoundException on fromChannel() if member not found")
  void should_throw_not_found_exception_on_fromChannel_if_member_not_found() {
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);
    doReturn(null).when(members).get(MEMBER_ID);

    NotFoundException notFoundException =
        assertThrows(
            NotFoundException.class,
            () -> this.routingService.fromChannel(CHANNEL_CONNECTION, CHANNEL_MESSAGE));

    assertThat(notFoundException).hasMessage("Member not found");
  }

  @Test
  @DisplayName("Should not send a message to the member on fromChannel() if he has no routes")
  void should_not_send_message_n_fromChannel_if_member_has_no_routes() {
    Member memberWithoutRoute = MEMBER.withRoutes(Set.of());
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);
    doReturn(memberWithoutRoute).when(members).get(MEMBER_ID);

    var payload = this.routingService.fromChannel(CHANNEL_CONNECTION, CHANNEL_MESSAGE);

    assertThat(payload)
        .isPresent()
        .get()
        .asInstanceOf(type(Notification.class))
        .isEqualTo(Notification.warn("%s is offline".formatted(MEMBER_NAME)));
  }

  @Test
  @DisplayName("Should send a message to the member on fromChannel()")
  void should_send_message_to_member() {
    String toMemberId = "ws:anotherId";
    RouteMessage routeMessage = channelRouteMessage(toMemberId);
    Id id = new Id(CHANNEL_NAME, toMemberId);
    Member member = MEMBER.withId(id);
    Notification response = Notification.plain("OK");
    MessageRouted messageRouted =
        new MessageRouted(
            CHANNEL, member, Direction.FROM_CHANNEL, TEXT_PAYLOAD, Map.of(MEMBER_ORIGIN, response));
    doReturn(CHANNEL).when(channels).get(CHANNEL_NAME);
    doReturn(member).when(members).get(id);
    doReturn(Optional.of(response)).when(router).send(any(Route.class), any(Payload.class));

    var payload = this.routingService.fromChannel(CHANNEL_CONNECTION, routeMessage);

    assertThat(payload).isEmpty();
    verify(eventPublisher, times(1)).publishEvent(messageRouted);
    verify(router, times(3)).send(any(Route.class), any(Payload.class));
  }

  @Test
  @DisplayName(
      "Should send message to all member's routes and return empty response on sendToMember()")
  void should_send_message_to_all_member_routes_and_return_empty_response() {
    Member member = MEMBER.withRoutes(Set.of(Route.of("ws:test1"), Route.of("tg:test2")));
    doReturn(Optional.empty()).when(router).send(any(Route.class), any(Payload.class));

    var response = this.routingService.sendToMember(member, Notification.plain("text"));

    assertThat(response).isEmpty();
    verify(router, times(2)).send(any(Route.class), any(Payload.class));
  }

  @Test
  @DisplayName("Should send message to all member's routes and return response on sendToMember()")
  void should_send_message_to_all_member_routes_and_return_response() {
    Route routeOne = Route.of("ws:test1");
    Route routeTwo = Route.of("tg:test2");
    Member member = MEMBER.withRoutes(Set.of(routeOne, routeTwo));
    Notification responsePayload = Notification.plain("OK");
    doReturn(Optional.of(responsePayload)).when(router).send(any(Route.class), any(Payload.class));

    var response = this.routingService.sendToMember(member, Notification.plain("text"));

    assertThat(response).isEqualTo(Map.of(routeOne, responsePayload, routeTwo, responsePayload));
    verify(router, times(2)).send(any(Route.class), any(Payload.class));
  }

  @Test
  @DisplayName("Should invoke fromMember() on fromRoute() if Route is MemberConnection")
  void should_invoke_fromMember_if_route_is_member_connection() {
    var response = Optional.of(Notification.plain("OK"));
    doReturn(MEMBER_CONNECTION).when(connections).get(MEMBER_ORIGIN);
    doReturn(response).when(spiedRoutingService).fromMember(MEMBER_CONNECTION, MEMBER_MESSAGE);

    var payload = this.spiedRoutingService.fromRoute(MEMBER_MESSAGE);

    assertThat(payload).isSameAs(response);
  }

  @Test
  @DisplayName("Should invoke fromChannel() on fromRoute() if Route is ChannelConnection")
  void should_invoke_fromChannel_if_route_is_channel_connection() {
    var response = Optional.of(Notification.plain("OK"));
    doReturn(CHANNEL_CONNECTION).when(connections).get(CHANNEL_ORIGIN);
    doReturn(response).when(spiedRoutingService).fromChannel(CHANNEL_CONNECTION, CHANNEL_MESSAGE);

    var payload = this.spiedRoutingService.fromRoute(CHANNEL_MESSAGE);

    assertThat(payload).isSameAs(response);
  }

  @Test
  @DisplayName("Should return empty response on fromRoute() if Route does not exist")
  void should_return_empty_response_if_route_does_not_exist() {
    doReturn(null).when(connections).get(MEMBER_ORIGIN);

    var payload = this.routingService.fromRoute(MEMBER_MESSAGE);

    assertThat(payload).isEmpty();
  }

  private static SendTextRecord textMessagePayload() {
    return new SendTextRecord("test", "8a", SendText.Mode.NEW, Instant.now());
  }

  private static RouteMessage memberRouteMessage() {
    return new RouteMessage(
        MEMBER_ORIGIN, MEMBER_RAW_ID, DEFAULT_LOCALE, TEXT_PAYLOAD, Optional.empty());
  }

  private static RouteMessage channelRouteMessage(String toMemberId) {
    return new RouteMessage(
        CHANNEL_ORIGIN, HOST_ID, DEFAULT_LOCALE, TEXT_PAYLOAD, Optional.ofNullable(toMemberId));
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import kite.core.domain.Connection.ChannelConnection;
import kite.core.domain.Connection.MemberConnection;
import kite.core.domain.Member.Id;
import kite.core.domain.command.Command.RouteMessage;
import kite.core.domain.event.Event;
import kite.core.domain.event.MessageRoutedBuilder;
import kite.core.domain.exception.ConflictException;
import kite.core.domain.exception.NotFoundException;
import kite.core.domain.payload.DeleteMessage;
import kite.core.domain.payload.MessagePayload;
import kite.core.domain.payload.Notification;
import kite.core.domain.payload.Payload;
import kite.core.domain.payload.SendText;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoutingService {

  private static final Logger log = LoggerFactory.getLogger(RoutingService.class);

  private final Connections connections;
  private final Members members;
  private final Channels channels;
  private final Router router;
  private final Event.Publisher eventPublisher;

  /**
   * @param connections
   * @param members
   * @param router
   */
  public RoutingService(
      Connections connections,
      Members members,
      Channels channels,
      Router router,
      Event.Publisher eventPublisher) {
    this.connections = connections;
    this.members = members;
    this.channels = channels;
    this.router = router;
    this.eventPublisher = eventPublisher;
  }

  public Optional<Payload> fromRoute(RouteMessage request) {
    var incoming = this.connections.get(request.origin());
    return switch (incoming) {
      case ChannelConnection c -> this.fromChannel(c, request);
      case MemberConnection c -> this.fromMember(c, request);
      case null -> Optional.empty();
    };
  }

  public Optional<Payload> fromMember(MemberConnection c, RouteMessage request) {
    if (!c.memberId().raw().equals(request.memberId())) {
      throw new ConflictException("You need to /join " + c.memberId().channelName());
    }
    Member member = this.members.get(c.memberId());
    if (null == member) {
      throw new NotFoundException("Member not found: " + c.memberId());
    }
    String channelName = member.id().channelName();
    Channel channel = this.channels.get(channelName);
    if (null == channel) {
      throw new NotFoundException("Channel not found: " + channelName);
    }
    var payload = prependPayloadText(member, request.payload());
    var response = sendToChannel(channel, payload);
    var singletonMap = response.map(r -> Map.of(channel.defaultRoute(), r)).orElse(Map.of());
    this.eventPublisher.publishEvent(
        MessageRoutedBuilder.builder()
            .channel(channel)
            .member(member)
            .direction(Direction.TO_CHANNEL)
            .request(payload)
            .responses(singletonMap)
            .build());
    return response;
  }

  public Optional<Payload> fromChannel(ChannelConnection c, RouteMessage request) {
    Channel channel = this.channels.get(c.channelName());
    if (null == channel) {
      throw new NotFoundException("Channel not found: " + c.channelName());
    }
    Id memberId =
        request
            .toMember()
            .map(rawId -> new Member.Id(c.channelName(), rawId))
            .or(channel::peerMember)
            .orElseThrow(() -> new NotFoundException("Unknown peer"));
    Member member = this.members.get(memberId);
    if (null == member) {
      throw new NotFoundException("Member not found");
    }
    if (member.routes().isEmpty()) {
      return Optional.of(Notification.warn("%s is offline".formatted(member.userName())));
    }
    MessagePayload payload = request.payload();
    final Map<Route, Payload> responses = this.sendToMember(member, payload);
    this.eventPublisher.publishEvent(
        MessageRoutedBuilder.builder()
            .channel(channel)
            .member(member)
            .direction(Direction.FROM_CHANNEL)
            .request(payload)
            .responses(responses)
            .build());
    // Bot cannot edit other's messages, so we delete host message and create a new one,
    // prefixed with the member id hashtag
    this.sendToRoute(channel.defaultRoute(), new DeleteMessage(payload.messageId(), Instant.now()));
    payload = prependPayloadText(member, payload);
    this.sendToRoute(channel.defaultRoute(), payload);
    return Optional.empty();
  }

  /**
   * Attaches hashtag with member id before payload's text
   *
   * @param client the id to attach as hashtag
   * @param payload payload to prepend
   * @return prepended payload
   */
  private MessagePayload prependPayloadText(Member client, MessagePayload payload) {
    if (payload instanceof SendText hasText) {
      String text = '#' + client.id().raw() + " " + client.userName() + '\n' + hasText.text();
      payload = hasText.overrideText(text);
    }
    return payload;
  }

  public Map<Route, Payload> sendToMember(Member member, Payload payload) {
    Map<Route, Payload> responses = new HashMap<>();
    for (var route : member.routes()) {
      var response = this.sendToRoute(route, payload);
      if (response.isPresent()) {
        responses.put(route, response.get());
      }
    }
    return responses;
  }

  public Optional<Payload> sendToChannel(Channel channel, Payload payload) {
    return this.sendToRoute(channel.defaultRoute(), payload);
  }

  public Optional<Payload> sendToRoute(Route route, Payload payload) {
    return this.router.send(route, payload);
  }
}

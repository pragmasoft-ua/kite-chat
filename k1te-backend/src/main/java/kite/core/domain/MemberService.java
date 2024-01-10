/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import kite.core.domain.Connection.ChannelConnection;
import kite.core.domain.Connection.MemberConnection;
import kite.core.domain.Member.Id;
import kite.core.domain.command.Command.DropChannel;
import kite.core.domain.command.Command.ExecuteCommand;
import kite.core.domain.command.Command.HostChannel;
import kite.core.domain.command.Command.Info;
import kite.core.domain.command.Command.Join;
import kite.core.domain.command.Command.Leave;
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
import kite.core.domain.payload.Payload;

public final class MemberService {

  private static final String ANONYMOUS_INFO =
      """
      You don't talk to any channels at the moment
      To start talking use /join <channel>
      For instructions use /help
      """;

  private final Event.Publisher eventPublisher;
  private final Members members;
  private final Channels channels;
  private final Connections connections;
  private final RoutingService routingService;
  private final URI wsApi;

  /**
   * @param members
   * @param connections
   * @param router
   */
  public MemberService(
      Event.Publisher eventPublsher,
      Members members,
      Channels channels,
      Connections connections,
      RoutingService routingService,
      URI wsApi) {
    this.eventPublisher = eventPublsher;
    this.members = members;
    this.channels = channels;
    this.connections = connections;
    this.routingService = routingService;
    this.wsApi = wsApi.getScheme().equals("wss") ? wsApi : rewriteScheme(wsApi, "wss");
  }

  public Optional<Payload> executeCommand(ExecuteCommand c) {
    var command = c.command();
    return switch (command) {
      case "/help" -> help(c.locale());
      case "/info" -> info(new Info(c.origin(), c.memberId(), c.locale()));
      case "/host" -> hostChannel(new HostChannel(c.origin(), c.args(), c.memberId(), c.locale()));
      case "/join" ->
          join(new Join(c.origin(), new Id(c.args(), c.memberId()), c.memberName(), c.locale()));
      case "/leave" -> leave(new Leave(c.origin(), c.locale()));
      case "/drop" -> dropChannel(new DropChannel(c.memberId(), c.locale()));
      case "/start", "/startgroup" -> start(c);
      default -> throw new ValidationException("Unsupported command " + command);
    };
  }

  public Optional<Payload> help(Locale locale) {
    var l10n = ResourceBundle.getBundle("l10n", locale);
    String help = l10n.getString("help");
    return Optional.of(Notification.plain(help));
  }

  /** TODO l10n, @see {@link #help(Locale)} */
  public Optional<Payload> info(Info command) {
    Route origin = command.origin();
    String rawMemberId = command.rawMemberId();
    String channelName = this.channels.getChannelName(rawMemberId);
    if (null != channelName) {
      return Optional.of(Notification.info("You are the host of the channel " + channelName));
    }
    Connection connection = this.connections.get(origin);
    if (connection instanceof ChannelConnection c) {
      return Optional.of(
          Notification.info("You can respond to messages in the channel " + c.channelName()));
    } else if (connection instanceof MemberConnection c && c.memberId().raw().equals(rawMemberId)) {
      return Optional.of(
          Notification.info("You are the member of the channel " + c.memberId().channelName()));
    }
    return Optional.of(Notification.info(ANONYMOUS_INFO));
  }

  /**
   * We expect
   *
   * <p>/start host__<channel>
   *
   * <p>same as /host <channel>
   *
   * <p>/start join__<channel> same as /start <channel> same as /join <channel>
   *
   * <p>joinAs /start join__<channel>__<memberId>
   *
   * <p>TODO SECURITY BREACH!!! Member can be easily impersonated, we don't have a protection
   */
  private Optional<Payload> start(ExecuteCommand c) {
    var origin = c.origin();
    var memberName = c.memberName();
    String memberId = c.memberId();
    String[] args = c.args().split("__", 3);
    var locale = c.locale();
    if (args.length > 0) {
      String subcommand = args[0].toLowerCase(locale);
      return switch (subcommand) {
        case "host" -> hostChannel(new HostChannel(origin, args[1], memberId, locale));
        case "join" ->
            args.length > 2
                ? joinAs(new Join(origin, new Member.Id(args[1], args[2]), memberName, locale))
                : join(new Join(origin, new Member.Id(args[1], memberId), memberName, locale));
        default -> join(new Join(origin, new Member.Id(subcommand, memberId), memberName, locale));
      };
    }
    return help(c.locale());
  }

  public Optional<Payload> hostChannel(HostChannel command) {
    Route origin = command.origin();
    String channelName = command.channelName();
    String hostId = command.hostId();
    String hostedChannel = this.channels.getChannelName(hostId);
    if (null != hostedChannel && !channelName.equals(hostedChannel)) {
      throw new ConflictException("You cannot host more than one channel");
    }
    Channel channel = this.channels.get(channelName);
    if (null != channel) {
      if (!channel.hostId().equals(hostId)) {
        throw new ConflictException("Channel name is already taken");
      }
      if (!channel.defaultRoute().equals(origin)) {
        var channelUpdated = ChannelBuilder.builder(channel).defaultRoute(origin).build();
        this.eventPublisher.publishEvent(new ChannelUpdated(channel, channelUpdated));
        return Optional.of(
            Notification.info(
                "Messages for channel %s will be sent here from now on".formatted(channelName)));
      }
    }
    channel =
        ChannelBuilder.builder()
            .name(channelName)
            .hostId(hostId)
            .defaultRoute(origin)
            .chatBot(Optional.empty())
            .peerMember(Optional.empty())
            .build();
    this.eventPublisher.publishEvent(new ChannelCreated(channel));
    String channelPublicUrl =
        this.wsApi.toString() + "?c=" + URLEncoder.encode(channelName, StandardCharsets.UTF_8);
    return Optional.of(
        Notification.info(
            "Created channel %s. Use URL %s to configure k1te chat frontend"
                .formatted(channelName, channelPublicUrl)));
  }

  public Optional<Payload> dropChannel(DropChannel command) {
    String hostId = command.hostId();
    String hostedChannel = this.channels.getChannelName(hostId);
    if (null == hostedChannel) {
      throw new NotFoundException("You don't host any channels to drop");
    }
    this.eventPublisher.publishEvent(new ChannelDropped(hostedChannel));
    var payload = Notification.info("You dropped channel %s".formatted(hostedChannel));
    return Optional.of(payload);
  }

  public Optional<Payload> join(Join command) {
    Route origin = command.origin();
    Id memberId = command.memberId();
    String memberName = command.memberName();
    Member member = this.members.get(memberId);
    if (null != member) {
      this.eventPublisher.publishEvent(new MemberConnected(memberId, origin));
      return Optional.empty();
    }
    member = new Member(memberId, memberName, Set.of(origin));
    this.eventPublisher.publishEvent(new MemberCreated(member));
    String channelName = memberId.channelName();
    Channel channel = this.channels.get(channelName);
    Objects.requireNonNull(channel, "Channel " + channelName);
    var notifyChannel =
        Notification.info(
            "%s joined channel %s".formatted(memberName, channelName).formatted(channelName));
    this.routingService.sendToRoute(channel.defaultRoute(), notifyChannel);
    return Optional.of(Notification.info("You joined channel " + channelName));
  }

  public Optional<Payload> joinAs(Join command) {
    join(command);
    var memberId = command.memberId();
    return Optional.of(Notification.info("You joined channel " + memberId.channelName()));
  }

  public Optional<Payload> leave(Leave command) {
    Route origin = command.origin();
    Connection connection = this.connections.get(origin);
    if (connection instanceof MemberConnection memberConnection) {
      var memberId = memberConnection.memberId();
      this.eventPublisher.publishEvent(new MemberDeleted(memberId));
      String channelName = memberId.channelName();
      Channel channel = this.channels.get(channelName);
      Objects.requireNonNull(channel, "Channel " + channelName);
      var notifyChannel =
          Notification.info("#%s left channel %s".formatted(memberId.raw(), channelName));
      this.routingService.sendToRoute(channel.defaultRoute(), notifyChannel);
      return Optional.of(Notification.info("You left channel " + channelName));
    }
    return Optional.of(Notification.warn("You are not joined to any channel"));
  }

  private static URI rewriteScheme(URI uri, String scheme) {
    try {
      return new URI(scheme, uri.getSchemeSpecificPart(), uri.getFragment());
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e);
    }
  }
}

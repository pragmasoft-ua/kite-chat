/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.event;

import kite.core.domain.Channel;

public sealed interface ChannelEvent extends Event {

  public record ChannelCreated(Channel channel) implements ChannelEvent {}

  public record ChannelUpdated(Channel before, Channel channel) implements ChannelEvent {}

  public record ChannelDropped(String channelName) implements ChannelEvent {}
}

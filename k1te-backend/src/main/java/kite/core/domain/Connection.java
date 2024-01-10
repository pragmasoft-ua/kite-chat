/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.util.Objects;

public sealed interface Connection {

  Route route();

  public record ChannelConnection(Route route, String channelName) implements Connection {
    public ChannelConnection {
      Objects.requireNonNull(route, "route");
      Objects.requireNonNull(channelName, "channelName");
    }
  }

  public record MemberConnection(Route route, Member.Id memberId) implements Connection {
    public MemberConnection {
      Objects.requireNonNull(route, "route");
      Objects.requireNonNull(memberId, "memberId");
    }
  }
}

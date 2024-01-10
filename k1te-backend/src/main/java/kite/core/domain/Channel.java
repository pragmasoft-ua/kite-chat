/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Objects;
import java.util.Optional;

@RecordBuilder
public record Channel(
    String name,
    String hostId,
    Route defaultRoute,
    Optional<Route> chatBot,
    Optional<Member.Id> peerMember) {

  public Channel {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(hostId, "hostId");
    Objects.requireNonNull(defaultRoute, "defaultRoute");
    chatBot = Objects.requireNonNullElse(chatBot, Optional.empty());
    peerMember = Objects.requireNonNullElse(peerMember, Optional.empty());
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Channel)) return false;
    Channel other = (Channel) obj;
    return Objects.equals(name, other.name);
  }
}

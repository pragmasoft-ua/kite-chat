/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain.command;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Locale;
import java.util.Optional;
import kite.core.domain.Member.Id;
import kite.core.domain.Route;
import kite.core.domain.payload.MessagePayload;

public sealed interface Command {
  Route origin();

  Locale locale();

  public record RouteMessage(
      Route origin,
      String memberId,
      Locale locale,
      MessagePayload payload,
      Optional<String> toMember)
      implements Command {}

  @RecordBuilder
  public record ExecuteCommand(
      Route origin, Locale locale, String memberId, String memberName, String command, String args)
      implements Command {

    public ExecuteCommand(
        Route origin, Locale locale, String memberId, String memberName, String command) {
      this(origin, locale, memberId, memberName, command, "");
    }
  }

  public record Info(Route origin, String rawMemberId, Locale locale) {}

  public record HostChannel(Route origin, String channelName, String hostId, Locale locale) {}

  public record DropChannel(String hostId, Locale locale) {}

  public record Join(Route origin, Id memberId, String memberName, Locale locale) {}

  public record Leave(Route origin, Locale locale) {}
}

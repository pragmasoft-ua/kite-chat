/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.event;

import kite.core.domain.Member;
import kite.core.domain.Route;

public sealed interface MemberEvent extends Event {

  public record MemberCreated(Member member) implements MemberEvent {}

  public record MemberConnected(Member.Id memberId, Route connected) implements MemberEvent {}

  public record MemberDisconnected(Member.Id memberId, Route disconnected) implements MemberEvent {}

  public record MemberDeleted(Member.Id memberId) implements MemberEvent {}
}

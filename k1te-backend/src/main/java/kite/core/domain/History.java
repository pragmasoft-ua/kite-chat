/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.time.Instant;
import kite.core.domain.payload.MessagePayload;

public interface History {

  public record Message(Member.Id memberId, MessagePayload payload, Direction direction) {}

  void append(Message message);

  default void append(Member.Id memberId, MessagePayload payload, Direction direction) {
    this.append(new Message(memberId, payload, direction));
  }

  Message find(Member.Id member, String messageId);

  void update(Member.Id member, String messageId, MessagePayload newPayload);

  void delete(Member.Id member, String messageId);

  void deleteAllForMember(Member.Id member);

  void deleteAllForChannel(String channelName);

  Iterable<Message> find(Query query);

  public enum Lookup {
    AFTER,
    BEFORE
  }

  public record Query(Member.Id member, Instant fromTime, Lookup lookup, int limit) {

    public static Query by(Member.Id member, Instant fromTime, Lookup lookup, int limit) {
      return new Query(member, fromTime, lookup, limit);
    }

    public static Query after(Member.Id member, Instant after) {
      return by(member, after, Lookup.AFTER, 20);
    }

    public static Query before(Member.Id member, Instant before) {
      return by(member, before, Lookup.BEFORE, 20);
    }
  }
}

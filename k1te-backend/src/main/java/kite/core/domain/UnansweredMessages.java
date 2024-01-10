/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.util.Optional;

public interface UnansweredMessages {

  void addUnansweredMessage(Member.Id client, Integer messageId);

  Optional<Integer> unansweredMessage(Member.Id client);

  void deleteUnansweredMessage(Member.Id client);

  void deleteUnansweredMessages(String channelName);
}

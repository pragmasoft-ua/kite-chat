/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;
import java.util.List;

public interface Messages {

  HistoryMessage persist(
      Member owner, String messageId, String content, Instant time, boolean incoming);

  HistoryMessage find(Member member, String messageId);

  List<HistoryMessage> findAll(Member member, String lastMessageId, Integer limit);
}

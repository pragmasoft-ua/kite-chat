/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import java.time.Instant;
import kite.core.domain.History;
import kite.core.domain.History.Lookup;
import kite.core.domain.History.Query;
import kite.core.domain.Member;
import kite.core.domain.Route;
import kite.core.domain.event.MemberEvent.MemberConnected;

public final class TgHistoryResender {

  private static final int MAX_RESEND_HISTORY_MESSAGES = 10;

  private final History history;
  private final TgConnector tgConnector;

  public TgHistoryResender(History history, TgConnector tgConnector) {
    this.history = history;
    this.tgConnector = tgConnector;
  }

  void onMemberConnected(MemberConnected event) {
    Member.Id memberId = event.memberId();
    Route newRoute = event.connected();
    if (!newRoute.provider().equals(TgConnector.TG)) return;
    var messages =
        history.find(Query.by(memberId, Instant.now(), Lookup.BEFORE, MAX_RESEND_HISTORY_MESSAGES));
    for (var message : messages) {
      // TODO optimize with TgSendBinary/TgSendText to use CopyMessage like in previous
      // implementation. this.tgConnector.bot is package private so can be used to construct them.
      this.tgConnector.send(newRoute, message.payload());
    }
  }
}

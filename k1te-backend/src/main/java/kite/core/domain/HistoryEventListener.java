/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import kite.core.domain.event.ChannelEvent.ChannelDropped;
import kite.core.domain.event.MessageRouted;
import kite.core.domain.payload.DeleteMessage;
import kite.core.domain.payload.MessagePayload;
import kite.core.domain.payload.SendBinary;
import kite.core.domain.payload.SendText;
import kite.core.domain.payload.SendText.Mode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class HistoryEventListener {

  private static final Logger log = LoggerFactory.getLogger(HistoryEventListener.class);

  private final History history;

  public HistoryEventListener(History history) {
    this.history = history;
  }

  void onMessageRouted(MessageRouted event) {
    var direction = event.direction();
    var member = event.member();
    var payload = event.request();
    var memberId = member.id();
    var messageId = payload.messageId();
    switch (payload) {
      case SendBinary p when p.mode() == Mode.NEW ->
          this.history.append(memberId, payload, direction);
      case SendText p when p.mode() == Mode.NEW ->
          this.history.append(memberId, payload, direction);
      case SendBinary p when p.mode() == Mode.EDITED ->
          this.history.update(memberId, messageId, payload);
      case SendText p when p.mode() == Mode.EDITED ->
          this.history.update(memberId, messageId, payload);
      case DeleteMessage p -> this.history.delete(memberId, messageId);
      case MessagePayload p -> log.warn("Ignored payload type: " + p.getClass().getName());
    }
  }

  void onChannelDropped(ChannelDropped event) {
    this.history.deleteAllForChannel(event.channelName());
  }
}

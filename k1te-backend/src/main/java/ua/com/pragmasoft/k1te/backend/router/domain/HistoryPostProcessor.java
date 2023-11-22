/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import ua.com.pragmasoft.k1te.backend.ws.PayloadEncoder;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;

public class HistoryPostProcessor implements RouterPostProcessor {

  private static final PayloadEncoder ENCODER = new PayloadEncoder();
  private final Channels channels;
  private final Messages messages;

  public HistoryPostProcessor(Channels channels, Messages messages) {
    this.channels = channels;
    this.messages = messages;
  }

  @Override
  public void accept(RoutingContext ctx) {
    String messageId = ctx.response.messageId();
    String destinationMessageId = ctx.response.destinationMessageId();
    if (!messageId.equals("-")) { // do nothing if it's join/left/switch/selfMessage messages
      String ownerMessageId;
      String toMessageId;
      if (Connector.connectorId(ctx.originConnection).equals(WsConnector.WS)) {
        ownerMessageId = destinationMessageId;
        toMessageId = destinationMessageId;
      } else if (Connector.connectorId(ctx.destinationConnection).equals(WsConnector.WS)) {
        ownerMessageId = messageId;
        toMessageId = messageId;
      } else {
        ownerMessageId = messageId;
        toMessageId = destinationMessageId;
      }

      this.channels.updateConnection(
          ctx.from, ctx.originConnection, ownerMessageId, ctx.response.delivered());
      this.channels.updateConnection(
          ctx.to, ctx.destinationConnection, toMessageId, ctx.response.delivered());

      String content = ENCODER.apply(ctx.request);
      if (ctx.from.isHost()) {
        this.messages.persist(ctx.to, toMessageId, content, ctx.response.delivered());
      } else {
        this.messages.persist(ctx.from, ownerMessageId, content, ctx.response.delivered());
      }
    }
  }
}

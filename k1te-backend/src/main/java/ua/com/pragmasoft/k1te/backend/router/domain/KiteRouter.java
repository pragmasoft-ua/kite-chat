/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.RoutingException;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;

public class KiteRouter implements Router {

  private static final Logger log = LoggerFactory.getLogger(KiteRouter.class);

  public static final String CONNECTOR_ID = "k1te";

  public static final String ATTR_FROM = "k1te.member.from";
  public static final String ATTR_TO = "k1te.member.to";

  private final Map<String, Connector> connectors = new HashMap<>(8);
  private final Channels channels;

  /**
   * @param channels
   */
  public KiteRouter(Channels channels) {
    this.channels = channels;
  }

  @Override
  public synchronized Router registerConnector(Connector connector) {
    this.connectors.put(connector.id(), connector);
    return this;
  }

  @Override
  public String id() {
    return CONNECTOR_ID;
  }

  @Override
  public void dispatch(RoutingContext ctx) throws KiteException {
    if (null == ctx.originConnection) {
      throw new RoutingException("unknown origin");
    }
    try {
      if (null == ctx.from) {
        ctx.from = this.channels.find(ctx.originConnection);
      }
      if (null == ctx.to) {
        ctx.to = this.channels.find(ctx.from.getChannelName(), ctx.from.getPeerMemberId());
      }
    } catch (NotFoundException notFound) {
      log.warn(notFound.getMessage());
      throw new RoutingException();
    }
    if (null == ctx.destinationConnection) {
      ctx.destinationConnection = ctx.to.getConnectionUri();
    }
    Connector connector = requiredConnector(Connector.connectorId(ctx.destinationConnection));
    connector.dispatch(ctx);
    MessageAck response = ctx.response;
    if (null == response) {
      throw new RoutingException("missing response from connector " + connector.id());
    }
    String messageId = response.messageId();
    if (Connector.connectorId(ctx.originConnection).equals(WsConnector.WS)) {
      messageId = response.destiationMessageId();
    }
    this.channels.updateUri(ctx.from, ctx.originConnection, messageId, response.delivered());
    this.channels.updatePeer(ctx.to, ctx.from.getId());
    this.channels.updatePeer(ctx.from, ctx.to.getId());
  }

  private synchronized Connector requiredConnector(String connectorId) throws NotFoundException {
    return Optional.ofNullable(this.connectors.get(connectorId))
        .orElseThrow(() -> new NotFoundException("No connector with id " + connectorId));
  }
}

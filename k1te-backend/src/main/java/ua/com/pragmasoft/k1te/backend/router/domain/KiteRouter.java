/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.RoutingException;
import ua.com.pragmasoft.k1te.backend.ws.PayloadEncoder;

public class KiteRouter implements Router {

  private static final Logger log = LoggerFactory.getLogger(KiteRouter.class);
  private static final PayloadEncoder ENCODER = new PayloadEncoder();

  public static final String CONNECTOR_ID = "k1te";

  public static final String ATTR_FROM = "k1te.member.from";
  public static final String ATTR_TO = "k1te.member.to";

  private final List<RouterPostProcessor> postProcessors;
  private final Map<String, Connector> connectors = new HashMap<>(8);
  private final Channels channels;
  private final Messages messages;

  /**
   * @param channels
   * @param messages
   */
  public KiteRouter(
      Channels channels, List<RouterPostProcessor> postProcessors, Messages messages) {
    Objects.requireNonNull(postProcessors, "Post processors");
    this.channels = channels;
    this.messages = messages;
    this.postProcessors = postProcessors;
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
      String connectionUri = ctx.to.getConnectionUri();
      if (connectionUri == null && !ctx.to.isHost()) {
        String content = ENCODER.apply(ctx.request);
        String messageId = ctx.request.messageId();
        Instant time = Instant.now();
        this.messages.persist(ctx.to, messageId, content, time);
        ctx.response = new MessageAck(messageId, messageId, time);
        log.debug("Member was not found. Messages {} was added to Member's history", messageId);
        return;
      }
      ctx.destinationConnection = connectionUri;
    }
    Connector connector = requiredConnector(Connector.connectorId(ctx.destinationConnection));
    connector.dispatch(ctx);
    MessageAck response = ctx.response;
    if (null == response) {
      throw new RoutingException("missing response from connector " + connector.id());
    }

    postProcessors.forEach(routerPostProcessor -> routerPostProcessor.accept(ctx));
  }

  private synchronized Connector requiredConnector(String connectorId) throws NotFoundException {
    return Optional.ofNullable(this.connectors.get(connectorId))
        .orElseThrow(() -> new NotFoundException("No connector with id " + connectorId));
  }
}

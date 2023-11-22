/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerUpdatePostProcessor implements RouterPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(PeerUpdatePostProcessor.class);

  private final Channels channels;

  public PeerUpdatePostProcessor(Channels channels) {
    this.channels = channels;
  }

  @Override
  public void accept(RoutingContext ctx) {
    this.channels.updatePeer(ctx.to, ctx.from.getId());
    this.channels.updatePeer(ctx.from, ctx.to.getId());
    log.debug("PeerMembers were updated");
  }
}

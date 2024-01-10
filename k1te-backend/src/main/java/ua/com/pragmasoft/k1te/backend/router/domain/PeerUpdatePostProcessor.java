/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PeerUpdatePostProcessor implements RouterPostProcessor {

  private static final Logger log = LoggerFactory.getLogger(PeerUpdatePostProcessor.class);

  @Override
  public void accept(RoutingContext ctx) {
    ctx.to.updatePeer(ctx.from.getId());
    ctx.from.updatePeer(ctx.to.getId());
    log.debug("PeerMembers were updated");
  }
}

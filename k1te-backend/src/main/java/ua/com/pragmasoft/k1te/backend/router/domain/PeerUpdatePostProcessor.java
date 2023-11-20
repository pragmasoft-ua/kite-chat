package ua.com.pragmasoft.k1te.backend.router.domain;

public class PeerUpdatePostProcessor implements RouterPostProcessor{

  private final Channels channels;

  public PeerUpdatePostProcessor(Channels channels) {
    this.channels = channels;
  }

  @Override
  public void accept(RoutingContext ctx) {
    this.channels.updatePeer(ctx.to, ctx.from.getId());
    this.channels.updatePeer(ctx.from, ctx.to.getId());
  }
}

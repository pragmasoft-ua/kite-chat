package ua.com.pragmasoft.k1te.router;

import java.util.concurrent.CompletableFuture;

public interface IConnector {

  ConnectorId id();

  CompletableFuture<RoutingContext> dispatchAsync(RoutingContext routingContext);

}

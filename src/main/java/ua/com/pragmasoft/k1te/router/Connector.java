package ua.com.pragmasoft.k1te.router;

import java.util.concurrent.CompletableFuture;

public interface Connector {

  ConnectorId id();

  <T extends Request<T, R>, R extends Response> CompletableFuture<R> sendAsync(T request,
      Route destination, Conversation conversation);

}

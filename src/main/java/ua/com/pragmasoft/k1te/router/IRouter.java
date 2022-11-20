package ua.com.pragmasoft.k1te.router;

import java.util.concurrent.CompletableFuture;

public interface IRouter {

  Conversation conversation(Route client, ChatId chatId);

  CompletableFuture<RoutingResponse> routeAsync(RoutingRequest routingRequest);

}

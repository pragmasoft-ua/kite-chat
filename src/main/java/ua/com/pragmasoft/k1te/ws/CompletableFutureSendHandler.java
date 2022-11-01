package ua.com.pragmasoft.k1te.ws;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import javax.websocket.SendHandler;
import javax.websocket.SendResult;

public class CompletableFutureSendHandler extends CompletableFuture<SendResult> {
  public CompletableFutureSendHandler(final Consumer<SendHandler> wrapped) {
    wrapped.accept(this::adapt);
  }

  void adapt(SendResult result) {
    if (result.isOK()) {
      this.complete(result);
    } else {
      this.completeExceptionally(result.getException());
    }
  }
}

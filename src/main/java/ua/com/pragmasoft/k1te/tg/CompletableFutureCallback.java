package ua.com.pragmasoft.k1te.tg;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import com.pengrad.telegrambot.Callback;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.TelegramException;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.response.BaseResponse;
import io.netty.util.concurrent.CompleteFuture;

/**
 * Adapts {@link TelegramBot#execute(BaseRequest, Callback)} async api to the {@link CompleteFuture}
 * api
 */
public class CompletableFutureCallback<T extends BaseRequest<T, R>, R extends BaseResponse>
    extends CompletableFuture<R> {
  public CompletableFutureCallback(final Consumer<Callback<T, R>> wrapped) {
    wrapped.accept(new CallbackAdapter());
  }

  final class CallbackAdapter implements Callback<T, R> {

    @Override
    public void onResponse(T request, R response) {
      if (response.isOk()) {
        CompletableFutureCallback.this.complete(response);
      } else {
        CompletableFutureCallback.this
            .completeExceptionally(new TelegramException(response.description(), response));
      }
    }

    @Override
    public void onFailure(T request, IOException e) {
      CompletableFutureCallback.this.completeExceptionally(e);
    }

  }

}

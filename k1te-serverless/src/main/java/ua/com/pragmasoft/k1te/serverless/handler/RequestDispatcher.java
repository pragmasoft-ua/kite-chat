/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.RequestStreamHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.arc.ClientProxy;
import io.quarkus.logging.Log;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.IdentityHashMap;
import java.util.Map;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.serverless.handler.event.LambdaEvent;

@Named(value = "main")
public class RequestDispatcher implements RequestStreamHandler {

  private final ObjectMapper objectMapper;
  private final Map<Type, RequestHandler> handlers = new IdentityHashMap<>();

  @Inject
  public RequestDispatcher(ObjectMapper objectMapper, Instance<RequestHandler<?, ?>> handlers) {
    this.objectMapper = objectMapper;
    registerHandlers(handlers);
  }

  @Override
  public void handleRequest(InputStream input, OutputStream output, Context context)
      throws IOException {
    LambdaEvent lambdaEvent = null;
    try (input) {
      lambdaEvent = objectMapper.readValue(input, LambdaEvent.class);
    } catch (Exception exception) {
      Log.errorf("Unsupported event type. {}", exception.getMessage());
      throw new UnsupportedOperationException(exception);
    }
    RequestHandler handler = handlers.get(lambdaEvent.getClass().getSuperclass());

    if (null == handler) {
      throw new NotFoundException("There is no Handler that can process your request");
    }

    objectMapper.writeValue(output, handler.handleRequest(lambdaEvent, context));
  }

  private void registerHandlers(Instance<RequestHandler<?, ?>> handlers) {
    handlers.forEach(
        handler -> {
          if (handler.getClass().getGenericInterfaces().length > 0) {
            Type[] arguments = null;
            if (handler.getClass().getGenericInterfaces()[0] == ClientProxy.class) {
              arguments =
                  ((ParameterizedType) handler.getClass().getSuperclass().getGenericInterfaces()[0])
                      .getActualTypeArguments();
            } else {
              arguments =
                  ((ParameterizedType) handler.getClass().getGenericInterfaces()[0])
                      .getActualTypeArguments();
            }
            if (arguments != null && arguments.length > 0) {
              this.handlers.put(arguments[0], handler);
            }
          }
        });
  }
}

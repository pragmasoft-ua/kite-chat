/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.serverless.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import ua.com.pragmasoft.k1te.serverless.handler.event.HttpV2LambdaEvent;
import ua.com.pragmasoft.k1te.serverless.handler.event.LambdaEvent;
import ua.com.pragmasoft.k1te.serverless.handler.event.WebSocketLambdaEvent;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LambdaHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void testHttpEvent() throws IOException {
    LambdaEvent lambdaEvent =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("http-event.json"), LambdaEvent.class);
    Assertions.assertSame(lambdaEvent.getClass(), HttpV2LambdaEvent.class);
  }

  @Test
  void testWebSocketConnectEvent() throws IOException {
    LambdaEvent lambdaEvent =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("ws-connect-event.json"),
            LambdaEvent.class);
    Assertions.assertSame(lambdaEvent.getClass(), WebSocketLambdaEvent.class);
  }

  @Test
  void testWebSocketMessageEvent() throws IOException {
    LambdaEvent lambdaEvent =
        objectMapper.readValue(
            getClass().getClassLoader().getResourceAsStream("ws-message-event.json"),
            LambdaEvent.class);
    Assertions.assertSame(lambdaEvent.getClass(), WebSocketLambdaEvent.class);
  }
}

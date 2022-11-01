package ua.com.pragmasoft.k1te.ws.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.Arrays;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EncodeException;
import javax.websocket.Encoder;
import org.apache.commons.codec.binary.Base16;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class TestSerialization {

  static final Logger log = Logger.getLogger(TestSerialization.class.getName());
  static final Encoder.Binary<KiteMsg> ENCODER = new MsgPackKiteEncoder();
  static final Decoder.Binary<KiteMsg> DECODER = new MsgPackKiteDecoder();

  static final ConnectedMsg CONNECTED_MSG = new ConnectedMsg("chatId", "userId");
  static final DisconnectedMsg DISCONNECTED_MSG = new DisconnectedMsg("userId");
  static final ErrorMsg ERROR_MSG = new ErrorMsg("Bad chatId", 404);
  static final PlaintextMsg PLAINTEXT_MSG =
      new PlaintextMsg("msgId", Instant.now(), MsgStatus.READ, "payload");

  static Stream<Arguments> argsProvider() {
    return Stream.of(Arguments.of(CONNECTED_MSG), Arguments.of(DISCONNECTED_MSG),
        Arguments.of(ERROR_MSG), Arguments.of(PLAINTEXT_MSG));
  }

  @ParameterizedTest
  @MethodSource("argsProvider")
  public void testSerializeAndDeserialize(KiteMsg msg) throws EncodeException, DecodeException {
    log.info("serializing " + msg);
    final var buf = ENCODER.encode(msg);
    log.info("serialized " + Arrays.toString(buf.array()));
    log.info("serialized hex " + new Base16().encodeAsString(buf.array()));
    final var deserialized = DECODER.decode(buf);
    log.info("deserialized " + deserialized.toString());
    assertEquals(msg, deserialized);
  }

}

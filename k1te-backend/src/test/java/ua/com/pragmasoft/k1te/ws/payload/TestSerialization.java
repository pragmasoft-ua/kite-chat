package ua.com.pragmasoft.k1te.ws.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import java.util.logging.Logger;
import jakarta.websocket.DecodeException;
import jakarta.websocket.EncodeException;
import org.junit.jupiter.api.Test;
import ua.com.pragmasoft.k1te.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.router.domain.payload.PlaintextMessage;
import ua.com.pragmasoft.k1te.ws.PayloadDecoder;
import ua.com.pragmasoft.k1te.ws.PayloadEncoder;

public class TestSerialization {

  static final Logger log = Logger.getLogger(TestSerialization.class.getName());

  static final PlaintextMessage PLAINTEXT_MSG =
      new PlaintextMessage("payload", "messageId", Instant.ofEpochSecond(100000));

  static final PayloadEncoder ENCODER = new PayloadEncoder();
  static final PayloadDecoder DECODER = new PayloadDecoder();

  @Test
  public void testSerializeAndDeserialize() throws EncodeException, DecodeException {
    final String serialized = ENCODER.encode(PLAINTEXT_MSG);
    log.info(serialized);
    final Payload deserialized = DECODER.decode(serialized);
    assertEquals(PLAINTEXT_MSG, deserialized);

  }

}

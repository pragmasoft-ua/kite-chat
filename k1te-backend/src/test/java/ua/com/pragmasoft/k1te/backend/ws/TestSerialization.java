package ua.com.pragmasoft.k1te.backend.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.PlaintextMessage;

class TestSerialization {

  static final Logger log = LoggerFactory.getLogger(TestSerialization.class);

  static final PlaintextMessage PLAINTEXT_MSG = new PlaintextMessage("payload", "messageId",
      Instant.ofEpochSecond(100000));

  static final PayloadEncoder ENCODER = new PayloadEncoder();
  static final PayloadDecoder DECODER = new PayloadDecoder();

  @Test
  void testSerializeAndDeserialize() {
    final String serialized = ENCODER.apply(PLAINTEXT_MSG);
    log.info(serialized);
    final Payload deserialized = DECODER.apply(serialized);
    assertEquals(PLAINTEXT_MSG, deserialized);

  }

}

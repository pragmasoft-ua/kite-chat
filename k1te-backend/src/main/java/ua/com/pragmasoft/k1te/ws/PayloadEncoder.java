package ua.com.pragmasoft.k1te.ws;

import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import jakarta.json.Json;
import jakarta.json.JsonWriter;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import ua.com.pragmasoft.k1te.router.domain.payload.ErrorResponse;
import ua.com.pragmasoft.k1te.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.router.domain.payload.PlaintextMessage;

public class PayloadEncoder implements Encoder.Text<Payload> {

  static final EnumMap<Payload.Type, BiConsumer<Payload, JsonWriter>> ENCODERS = new EnumMap<>(Payload.Type.class);

  static {
    ENCODERS.put(Payload.Type.ACK, PayloadEncoder::encodeAck);
    ENCODERS.put(Payload.Type.ERROR, PayloadEncoder::encodeError);
    ENCODERS.put(Payload.Type.PLAINTEXT, PayloadEncoder::encodePlaintext);
  }

  @Override
  public void init(EndpointConfig config) {
    // Empty init
  }

  @Override
  public void destroy() {
    // Empty destroy
  }

  @Override
  public String encode(Payload payload) throws EncodeException {
    var sw = new StringWriter();
    try (var jw = Json.createWriter(sw)) {
      final var type = payload.type();
      var encoder = ENCODERS.get(type);
      Objects.requireNonNull(encoder, "No encoder for " + type);
      encoder.accept(payload, jw);
      return sw.toString();
    }
  }

  private static void encodeAck(Payload payload, JsonWriter jw) {
    var ack = (MessageAck) payload;
    var array = Json
        .createArrayBuilder()
        .add(Payload.Type.ACK.ordinal())
        .add(ack.messageId())
        .add(ack.destiationMessageId())
        .add(ack.delivered().toString())
        .build();
    jw.writeArray(array);
  }

  private static void encodeError(Payload payload, JsonWriter jw) {
    var error = (ErrorResponse) payload;
    var array = Json
        .createArrayBuilder()
        .add(Payload.Type.ERROR.ordinal())
        .add(error.reason())
        .add(error.code())
        .build();
    jw.writeArray(array);
  }

  private static void encodePlaintext(Payload payload, JsonWriter jw) {
    var message = (PlaintextMessage) payload;
    var array = Json
        .createArrayBuilder()
        .add(Payload.Type.PLAINTEXT.ordinal())
        .add(message.text())
        .add(message.messageId())
        .add(message.created().toString())
        .build();
    jw.writeArray(array);
  }

}

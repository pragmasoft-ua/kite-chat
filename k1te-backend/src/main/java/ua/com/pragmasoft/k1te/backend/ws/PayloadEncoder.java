package ua.com.pragmasoft.k1te.backend.ws;

import java.io.StringWriter;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

import jakarta.json.Json;
import jakarta.json.JsonWriter;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.ErrorResponse;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.PlaintextMessage;

public class PayloadEncoder implements Function<Payload, String> {

  static final EnumMap<Payload.Type, BiConsumer<Payload, JsonWriter>> ENCODERS = new EnumMap<>(Payload.Type.class);

  static {
    ENCODERS.put(Payload.Type.ACK, PayloadEncoder::encodeAck);
    ENCODERS.put(Payload.Type.OK, PayloadEncoder::encodeTypeOnlyPayload);
    ENCODERS.put(Payload.Type.ERR, PayloadEncoder::encodeError);
    ENCODERS.put(Payload.Type.TXT, PayloadEncoder::encodePlaintext);
    ENCODERS.put(Payload.Type.PONG, PayloadEncoder::encodeTypeOnlyPayload);
  }

  @Override
  public String apply(Payload payload) {
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
        .add(payload.type().name())
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
        .add(payload.type().name())
        .add(error.reason())
        .add(error.code())
        .build();
    jw.writeArray(array);
  }

  private static void encodePlaintext(Payload payload, JsonWriter jw) {
    var message = (PlaintextMessage) payload;
    var array = Json
        .createArrayBuilder()
        .add(payload.type().name())
        .add(message.text())
        .add(message.messageId())
        .add(message.created().toString())
        .build();
    jw.writeArray(array);
  }

  private static void encodeTypeOnlyPayload(Payload payload, JsonWriter jw) {
    var array = Json
        .createArrayBuilder()
        .add(payload.type().name())
        .build();
    jw.writeArray(array);
  }

}

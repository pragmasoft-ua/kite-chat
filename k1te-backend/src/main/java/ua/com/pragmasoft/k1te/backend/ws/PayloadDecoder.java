package ua.com.pragmasoft.k1te.backend.ws;

import java.io.StringReader;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.JoinChannel;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.PlaintextMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload.Type;

public class PayloadDecoder implements Function<String, Payload> {

  private static final Logger log = LoggerFactory.getLogger(PayloadDecoder.class);

  static final EnumMap<Type, Function<JsonArray, Payload>> DECODERS = new EnumMap<>(Type.class);

  static {
    DECODERS.put(Type.JOIN, PayloadDecoder::decodeJoinChannel);
    DECODERS.put(Type.TXT, PayloadDecoder::decodePlaintextMessage);
  }

  @Override
  public Payload apply(String text) {

    log.debug("decode {}", text);

    try (var reader = Json.createReader(new StringReader(text))) {
      var array = reader.readArray();
      String typeLabel = array.getString(0);
      Type type = Type.valueOf(typeLabel);
      var decoder = DECODERS.get(type);
      Objects.requireNonNull(decoder, "No decoder for " + type);
      return decoder.apply(array);
    }

  }

  private static Payload decodeJoinChannel(JsonArray array) {
    Objects.checkIndex(2, array.size());
    String memberId = array.getString(1);
    String memberName = array.getString(2, memberId);
    return new JoinChannel(memberId, memberName);
  }

  private static Payload decodePlaintextMessage(JsonArray array) {
    Objects.checkIndex(3, array.size());
    String text = array.getString(1);
    String messageId = array.getString(2);
    Instant created = Instant.parse(array.getString(3));
    return new PlaintextMessage(
        text,
        messageId,
        created);
  }

}

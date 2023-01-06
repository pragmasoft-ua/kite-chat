package ua.com.pragmasoft.k1te.ws;

import java.io.StringReader;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;
import javax.json.Json;
import javax.json.JsonArray;
import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;
import ua.com.pragmasoft.k1te.router.domain.payload.JoinChannel;
import ua.com.pragmasoft.k1te.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.router.domain.payload.Payload.Type;
import ua.com.pragmasoft.k1te.router.domain.payload.PlaintextMessage;

public class PayloadDecoder implements Decoder.Text<Payload> {

  static final EnumMap<Type, Function<JsonArray, Payload>> DECODERS =
      new EnumMap<>(Type.class);

  static {
    DECODERS.put(Type.JOIN, PayloadDecoder::decodeJoinChannel);
    DECODERS.put(Type.PLAINTEXT, PayloadDecoder::decodePlaintextMessage);
  }

  @Override
  public void init(EndpointConfig config) {
    // empty init
  }

  @Override
  public void destroy() {
    // empty destroy
  }

  @Override
  public Payload decode(String text) throws DecodeException {

    try (var reader = Json.createReader(new StringReader(text))) {
      var array = reader.readArray();
      int typeOrdinal = array.getInt(0);
      Type type = Type.values()[typeOrdinal];
      return DECODERS.get(type).apply(array);
    }

  }

  @Override
  public boolean willDecode(String text) {
    return true;
  }

  private static Payload decodeJoinChannel(JsonArray array) {
    String memberId = array.getString(1);
    if (array.size() < 3) {
      return new JoinChannel(memberId);
    } else {
      String memberName = array.getString(2);
      return new JoinChannel(memberId, memberName);
    }
  }

  private static Payload decodePlaintextMessage(JsonArray array) {
    Objects.checkIndex(3, array.size());
    String text = array.getString(1);
    String messageId = array.getString(2);
    Instant created = Instant.ofEpochSecond(array.getJsonNumber(3).longValue());
    return new PlaintextMessage(
        text,
        messageId,
        created);
  }
}

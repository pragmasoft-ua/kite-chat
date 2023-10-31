/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.ws;

import jakarta.json.Json;
import jakarta.json.JsonArray;
import java.io.StringReader;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.JoinChannel;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload.Type;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Ping;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.PlaintextMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadRequest;

public class PayloadDecoder implements Function<String, Payload> {

  private static final Logger log = LoggerFactory.getLogger(PayloadDecoder.class);

  static final EnumMap<Type, Function<JsonArray, Payload>> DECODERS = new EnumMap<>(Type.class);

  static {
    DECODERS.put(Type.JOIN, PayloadDecoder::decodeJoinChannel);
    DECODERS.put(Type.TXT, PayloadDecoder::decodePlaintextMessage);
    DECODERS.put(Type.BIN, PayloadDecoder::decodeBinaryMessage);
    DECODERS.put(Type.UPL, PayloadDecoder::decodeUploadRequest);
    DECODERS.put(Type.PING, PayloadDecoder::decodePing);
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
    Objects.checkIndex(3, array.size());
    String memberId = array.getString(1);
    String memberName = array.getString(2, memberId);
    String channelName = array.getString(3);
    return new JoinChannel(memberId, memberName, channelName);
  }

  private static Payload decodePlaintextMessage(JsonArray array) {
    Objects.checkIndex(3, array.size());
    String messageId = array.getString(1);
    String text = array.getString(2);
    Instant timestamp = Instant.parse(array.getString(3));
    return new PlaintextMessage(text, messageId, timestamp);
  }

  private static Payload decodeBinaryMessage(JsonArray array) {
    Objects.checkIndex(6, array.size());
    var messageId = array.getString(1);
    var url = array.getString(2);
    var fileName = array.getString(3);
    var fileType = array.getString(4);
    var fileSize = array.getJsonNumber(5).longValueExact();
    Instant timestamp = Instant.parse(array.getString(6));
    return new BinaryMessage(url, fileName, fileType, fileSize, messageId, timestamp);
  }

  private static Payload decodeUploadRequest(JsonArray array) {
    Objects.checkIndex(5, array.size());
    var messageId = array.getString(1);
    var fileName = array.getString(2);
    var fileType = array.getString(3);
    var fileSize = array.getJsonNumber(4).longValueExact();
    Instant timestamp = Instant.parse(array.getString(5));
    return new UploadRequest(fileName, fileType, fileSize, messageId, timestamp);
  }

  private static Payload decodePing(JsonArray array) {
    return new Ping();
  }
}

package ua.com.pragmasoft.humane.payload;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BiFunction;

import javax.websocket.DecodeException;
import javax.websocket.Decoder;
import javax.websocket.EndpointConfig;

import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;

public class MsgPackHumaneDecoder implements Decoder.Binary<HumaneMsg> {

  /**
   * List of message decoders. They are accessed by index, so order must match constants defined in
   * the {@link HumaneMsg}
   */
  static final List<BiFunction<MessageUnpacker, Integer, HumaneMsg>> DECODERS =
      List.of(MsgPackHumaneDecoder::decodeConnected, MsgPackHumaneDecoder::decodeDisconnected,
          MsgPackHumaneDecoder::decodeError, MsgPackHumaneDecoder::decodePlaintext);

  @Override
  public void init(EndpointConfig config) {
    // empty init
  }

  @Override
  public void destroy() {
    // empty destroy
  }

  @Override
  public HumaneMsg decode(ByteBuffer bytes) throws DecodeException {
    short type = -1;
    try (final var unpacker = MessagePack.newDefaultUnpacker(bytes)) {
      var len = unpacker.unpackArrayHeader();
      type = unpacker.unpackShort();
      return DECODERS.get(type).apply(unpacker, len);
    } catch (IndexOutOfBoundsException oob) {
      throw new DecodeException(bytes, "Unsupported msg type %X".formatted(type));
    } catch (Exception e) {
      throw new DecodeException(bytes, e.getLocalizedMessage(), e);
    }
  }

  @Override
  public boolean willDecode(ByteBuffer bytes) {
    return true;
  }

  static HumaneMsg decodeConnected(MessageUnpacker unpacker, int len) {
    assertLength(3, len);
    try {
      return new ConnectedMsg(unpacker.unpackString(), unpacker.unpackString());
    } catch (IOException e) {
      throw new IllegalStateException(e.getLocalizedMessage(), e);
    }
  }

  static HumaneMsg decodeDisconnected(MessageUnpacker unpacker, int len) {
    assertLength(2, len);
    try {
      return new DisconnectedMsg(unpacker.unpackString());
    } catch (IOException e) {
      throw new IllegalStateException(e.getLocalizedMessage(), e);
    }
  }

  static HumaneMsg decodeError(MessageUnpacker unpacker, int len) {
    assertLength(3, len);
    try {
      return new ErrorMsg(unpacker.unpackString(), unpacker.unpackInt());
    } catch (IOException e) {
      throw new IllegalStateException(e.getLocalizedMessage(), e);
    }
  }

  static HumaneMsg decodePlaintext(MessageUnpacker unpacker, int len) {
    assertLength(5, len);
    try {
      return new PlaintextMsg(unpacker.unpackString(), unpacker.unpackTimestamp(),
          MsgStatus.values()[unpacker.unpackByte()], unpacker.unpackString());
    } catch (IOException e) {
      throw new IllegalStateException(e.getLocalizedMessage(), e);
    }
  }

  /*
   * Don't forget to count type field which is already upacked when decodeXXX method is called so +1
   * to the expected
   */
  static void assertLength(int expected, int actual) {
    if (actual != expected)
      throw new IllegalStateException(
          "Expected %d fields in encoded message but got %d instead".formatted(expected, actual));
  }
}

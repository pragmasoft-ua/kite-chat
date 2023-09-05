package ua.com.pragmasoft.k1te.server.ws.application;

import io.quarkus.logging.Log;
import jakarta.websocket.DecodeException;
import jakarta.websocket.Decoder;
import jakarta.websocket.EndpointConfig;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.PayloadDecoder;

public class PayloadDecoderAdapter implements Decoder.Text<Payload> {

  static final PayloadDecoder DECODER = new PayloadDecoder();

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

    Log.debug("decode " + text);

    try {
      return DECODER.apply(text);
    } catch (Exception e) {
      throw new DecodeException(text, e.getMessage(), e);
    }

  }

  @Override
  public boolean willDecode(String text) {
    return true;
  }

}

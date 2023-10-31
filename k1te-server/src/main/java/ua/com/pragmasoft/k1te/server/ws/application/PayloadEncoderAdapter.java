/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.ws.application;

import jakarta.websocket.EncodeException;
import jakarta.websocket.Encoder;
import jakarta.websocket.EndpointConfig;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.PayloadEncoder;

public class PayloadEncoderAdapter implements Encoder.Text<Payload> {

  static final PayloadEncoder ENCODER = new PayloadEncoder();

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
    try {
      return ENCODER.apply(payload);
    } catch (Exception e) {
      throw new EncodeException(payload, e.getMessage(), e);
    }
  }
}

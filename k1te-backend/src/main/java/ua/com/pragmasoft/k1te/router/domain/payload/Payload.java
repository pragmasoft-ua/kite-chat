package ua.com.pragmasoft.k1te.router.domain.payload;

import java.io.Serializable;

public sealed interface Payload
    extends Serializable permits CommandPayload, MessagePayload, ErrorResponse, MessageAck {

  Type type();

  public enum Type {
    JOIN, ACK, ERROR, PLAINTEXT
  }

}

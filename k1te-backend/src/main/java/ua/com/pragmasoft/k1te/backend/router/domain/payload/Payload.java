package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.io.Serializable;

public sealed interface Payload
    extends Serializable permits JoinChannel, MessagePayload, ErrorResponse, MessageAck, Ping, Pong {

  Type type();

  public enum Type {
    JOIN, PLAINTEXT, ACK, ERROR, PING, PONG
  }

}

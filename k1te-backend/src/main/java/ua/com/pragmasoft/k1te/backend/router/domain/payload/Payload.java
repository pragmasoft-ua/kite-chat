package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.io.Serializable;

public sealed interface Payload
    extends Serializable permits JoinChannel, MessagePayload, OkResponse, ErrorResponse, MessageAck, Ping, Pong {

  Type type();

  public enum Type {

    JOIN("Join request"),
    ACK("Acknowledge"),
    OK("OK"),
    ERR("Error"),
    TXT("Plaintext message"),
    BIN("Binary message"),
    UPL("Upload"),
    PING("Ping"),
    PONG("Pong");

    public final String label;

    Type(String label) {
      this.label = label;
    }

  }

}

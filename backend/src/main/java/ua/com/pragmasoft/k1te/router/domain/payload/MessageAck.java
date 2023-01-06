package ua.com.pragmasoft.k1te.router.domain.payload;

import java.time.Instant;
import ua.com.pragmasoft.k1te.router.domain.Id;

public record MessageAck(String messageId, String destiationMessageId, Instant delivered)
    implements Payload {

  public MessageAck(String messageId, String destiationMessageId, Instant delivered) {
    this.messageId = Id.validate(messageId);
    this.destiationMessageId = Id.validate(destiationMessageId);
    this.delivered = delivered;
  }

  public MessageAck(String messageId, String destiationMessageId) {
    this(messageId, destiationMessageId, Instant.now());
  }

  public MessageAck(String messageId) {
    this(messageId, messageId);
  }

  @Override
  public Type type() {
    return Type.ACK;
  }

}

package ua.com.pragmasoft.k1te.router;

import java.io.Serializable;
import java.time.Instant;

public record RoutingRequest(Route origin, ConversationId conversationId, Serializable payload,
    MessageId originMessageId, Instant created) {

  public RoutingRequest(Route origin, ConversationId conversationId, Serializable payload,
      MessageId originMessageId) {
    this(origin, conversationId, payload, originMessageId, Instant.now());
  }

  public RoutingRequest(Route origin, ConversationId conversationId, Serializable payload) {
    this(origin, conversationId, payload, MessageId.MISSING);
  }
}

package ua.com.pragmasoft.k1te.chat;

import java.time.Instant;
import java.util.Optional;

public record MessageResponse(MessageId id, MessageId requestId, Route origin, Instant timestamp,
        Optional<ConversationId> conversationId) implements Response {

}

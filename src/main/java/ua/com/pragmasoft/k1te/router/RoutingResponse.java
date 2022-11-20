package ua.com.pragmasoft.k1te.router;

import java.time.Instant;

public record RoutingResponse(MessageId destinationMessageId, Instant delivered) {

    public RoutingResponse(MessageId destinationMessageId) {
        this(destinationMessageId, Instant.now());
    }
}

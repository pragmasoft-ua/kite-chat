package ua.com.pragmasoft.humane.payload;

import java.util.Objects;

public record ConnectedMsg(String endpoint, String userId) implements HumaneMsg {

    public ConnectedMsg(String endpoint, String userId) {
        this.endpoint = endpoint;
        this.userId = Objects.requireNonNull(userId, "userId is required");
    }

    @Override
    public short type() {
        return CONNECTED;
    }

}

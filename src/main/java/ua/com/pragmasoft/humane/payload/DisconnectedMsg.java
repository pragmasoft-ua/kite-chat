package ua.com.pragmasoft.humane.payload;

public record DisconnectedMsg(String userId) implements HumaneMsg {
    @Override
    public short type() {
        return DISCONNECTED;
    }

}

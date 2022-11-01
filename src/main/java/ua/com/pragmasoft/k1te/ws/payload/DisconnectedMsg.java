package ua.com.pragmasoft.k1te.ws.payload;

public record DisconnectedMsg(String userId) implements KiteMsg {
    @Override
    public short type() {
        return DISCONNECTED;
    }

}

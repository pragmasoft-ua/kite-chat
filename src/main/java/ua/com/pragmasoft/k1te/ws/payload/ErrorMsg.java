package ua.com.pragmasoft.k1te.ws.payload;

public record ErrorMsg(String reason, int code) implements KiteMsg {
    @Override
    public short type() {
        return ERROR;
    }

}

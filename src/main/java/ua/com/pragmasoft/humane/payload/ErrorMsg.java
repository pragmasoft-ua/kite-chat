package ua.com.pragmasoft.humane.payload;

public record ErrorMsg(String reason, int code) implements HumaneMsg {
    @Override
    public short type() {
        return ERROR;
    }

}
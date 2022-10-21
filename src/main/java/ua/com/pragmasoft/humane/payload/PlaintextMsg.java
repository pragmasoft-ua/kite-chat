package ua.com.pragmasoft.humane.payload;

import java.time.Instant;

public record PlaintextMsg(String msgId, Instant timestamp, MsgStatus status, String payload)
                implements HumaneMsg {
        @Override
        public short type() {
                return PLAINTEXT;
        }

}

package ua.com.pragmasoft.k1te.ws.payload;

import java.time.Instant;

public record PlaintextMsg(String msgId, Instant timestamp, MsgStatus status, String payload)
                implements KiteMsg {

        public PlaintextMsg(String msgId, String payload) {
                this(msgId, Instant.now(), MsgStatus.UNKNOWN, payload);
        }

        @Override
        public short type() {
                return PLAINTEXT;
        }

}

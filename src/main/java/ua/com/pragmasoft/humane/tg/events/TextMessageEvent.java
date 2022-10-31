package ua.com.pragmasoft.humane.tg.events;

import java.time.Instant;

public record TextMessageEvent(String messageId, String from, String text, Instant timestamp) {

}

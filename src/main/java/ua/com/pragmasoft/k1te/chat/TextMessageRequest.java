package ua.com.pragmasoft.k1te.chat;

import java.time.Instant;
import java.util.Optional;

@SuppressWarnings("java:S6218")
public record TextMessageRequest(MessageId id, Route origin, Instant timestamp, String text,
    Entity[] entities, Optional<ConversationId> conversationId)
    implements Request<TextMessageRequest, MessageResponse> {

  public record Entity(Kind kind, int start, int end) {

    public enum Kind {
      HASHTAG, URL, EMAIL, PHONE_NUMBER, BOLD, ITALIC, CODE, PRE, UNDERLINE, STRIKETHROUGH
    }

    public String extract(String text) {
      var start = this.start();
      if (Kind.HASHTAG == this.kind) {
        start++;
      }
      return text.substring(start, this.end);
    }

  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;
import java.util.List;

public interface Messages {

  HistoryMessage persist(Member owner, String messageId, String content, Instant time);

  HistoryMessage find(Member member, String messageId);

  List<HistoryMessage> findAll(MessagesRequest request);

  class MessagesRequest {
    private String connectionUri;
    private Member messagesOwner;
    private Instant lastMessageTime;
    private String lastMessageId;
    private Integer limit;
    private boolean lastMessageByConnection;

    private MessagesRequest() {}

    public static MessagesRequestBuilder builder() {
      return new MessagesRequestBuilder();
    }

    public String getConnectionUri() {
      return connectionUri;
    }

    public Member getMessagesOwner() {
      return messagesOwner;
    }

    public Instant getLastMessageTime() {
      return lastMessageTime;
    }

    public String getLastMessageId() {
      return lastMessageId;
    }

    public Integer getLimit() {
      return limit;
    }

    public boolean isLastMessageByConnection() {
      return lastMessageByConnection;
    }

    public static class MessagesRequestBuilder {
      private final MessagesRequest request;

      private MessagesRequestBuilder() {
        this.request = new MessagesRequest();
      }

      public MessagesRequestBuilder connectionUri(String connectionUri) {
        this.request.connectionUri = connectionUri;
        return this;
      }

      public MessagesRequestBuilder member(Member member) {
        this.request.messagesOwner = member;
        return this;
      }

      public MessagesRequestBuilder lastMessageTime(Instant lastMessageTime) {
        this.request.lastMessageTime = lastMessageTime;
        return this;
      }

      public MessagesRequestBuilder lastMessageId(String lastMessageId) {
        this.request.lastMessageId = lastMessageId;
        return this;
      }

      public MessagesRequestBuilder limit(Integer limit) {
        this.request.limit = limit;
        return this;
      }

      public MessagesRequestBuilder lastMessageByConnection(boolean flag) {
        this.request.lastMessageByConnection = flag;
        return this;
      }

      public MessagesRequest build() {
        return this.request;
      }
    }
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Duration;
import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import ua.com.pragmasoft.k1te.backend.router.domain.HistoryMessage;

@DynamoDbBean
public class DynamoDbHistoryMessage implements HistoryMessage {
  public static final Duration DEFAULT_TIMEOUT = Duration.ofDays(92);

  private String id;
  private String messageId;
  private String content;
  private Instant time;
  private long ttl;

  public DynamoDbHistoryMessage(String id, String messageId, String content, Instant time) {
    this.id = id;
    this.messageId = messageId;
    this.content = content;
    this.time = time;
    this.ttl = Instant.now().plus(DEFAULT_TIMEOUT).getEpochSecond();
  }

  public DynamoDbHistoryMessage() {}

  public static String buildId(String channelName, String memberId) {
    return channelName + ":" + memberId;
  }

  @DynamoDbPartitionKey
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @Override
  public String getChannelName() {
    String[] parsedId = id.split(":", 2);
    return parsedId[0];
  }

  @Override
  public String getMemberId() {
    String[] parsedId = id.split(":", 2);
    return parsedId[1];
  }

  @Override
  @DynamoDbSortKey
  public String getMessageId() {
    return messageId;
  }

  public void setMessageId(String messageId) {
    this.messageId = messageId;
  }

  @Override
  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  @Override
  public Instant getTime() {
    return time;
  }

  public void setTime(Instant time) {
    this.time = time;
  }

  public long getTtl() {
    return ttl;
  }

  public void setTtl(long ttl) {
    this.ttl = ttl;
  }

  @Override
  public String toString() {
    return "DynamoDbMessage{"
        + "id='"
        + id
        + '\''
        + ", messageId='"
        + messageId
        + '\''
        + ", content='"
        + content
        + '\''
        + ", time="
        + time
        + ", ttl="
        + ttl
        + '}';
  }
}

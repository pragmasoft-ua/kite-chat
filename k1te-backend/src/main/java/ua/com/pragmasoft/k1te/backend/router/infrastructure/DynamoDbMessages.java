/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.HistoryMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.router.domain.Messages;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;

public class DynamoDbMessages implements Messages {

  public static final String MESSAGES = "Messages";

  private final String messagesTableName;
  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbTable<DynamoDbHistoryMessage> messageTable;

  public DynamoDbMessages(DynamoDbEnhancedClient enhancedDynamo, String serverlessEnvironmentName) {
    this.messagesTableName =
        null != serverlessEnvironmentName ? serverlessEnvironmentName + '.' + MESSAGES : MESSAGES;
    this.enhancedDynamo = enhancedDynamo;
    this.messageTable =
        this.enhancedDynamo.table(
            this.messagesTableName, TableSchema.fromClass(DynamoDbHistoryMessage.class));
  }

  @Override
  public HistoryMessage persist(
      Member owner, String messageId, String content, Instant time, boolean incoming) {
    Objects.requireNonNull(owner);
    Objects.requireNonNull(messageId);
    Objects.requireNonNull(content);
    Objects.requireNonNull(time);
    String id = DynamoDbHistoryMessage.buildId(owner.getChannelName(), owner.getId());

    DynamoDbHistoryMessage dbMessage =
        new DynamoDbHistoryMessage(id, messageId, content, time, incoming);
    try {
      this.messageTable.putItem(dbMessage);
      return dbMessage;
    } catch (Exception e) {
      throw new KiteException(e.getMessage(), e);
    }
  }

  @Override
  public List<HistoryMessage> findAll(Member member, String lastMessageId, Integer limit) {
    Objects.requireNonNull(member);
    Objects.requireNonNull(limit);

    Key.Builder keyBuilder =
        Key.builder()
            .partitionValue(
                DynamoDbHistoryMessage.buildId(member.getChannelName(), member.getId()));

    QueryConditional queryConditional =
        lastMessageId == null || lastMessageId.isEmpty()
            ? QueryConditional.keyEqualTo(keyBuilder.build())
            : QueryConditional.sortGreaterThan(keyBuilder.sortValue(lastMessageId).build());

    return this.messageTable
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .scanIndexForward(false)
                .limit(limit)
                .build())
        .items()
        .stream()
        .limit(limit)
        .map(HistoryMessage.class::cast)
        .toList();
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.*;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.*;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;

public class DynamoDbMessages implements Messages {

  public static final String MESSAGES_TABLE = "Messages";
  private static final String MESSAGES_TIME_INDEX = "MessageTime";

  private static final String MESSAGES_ID_ATTRIBUTE = "id";
  private static final String MESSAGES_TIME_ATTRIBUTE = "time";
  private static final String MESSAGES_CONTENT_ATTRIBUTE = "content";
  private static final String MESSAGES_MESSAGE_ID_ATTRIBUTE = "messageId";
  private static final String MESSAGES_INCOMING_ATTRIBUTE = "incoming";

  private final Channels channels;

  private final String messagesTableName;
  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbClient dynamoDbClient;
  private final DynamoDbTable<DynamoDbHistoryMessage> messageTable;

  public DynamoDbMessages(
      Channels channels,
      DynamoDbEnhancedClient enhancedDynamo,
      DynamoDbClient dynamoDbClient,
      String serverlessEnvironmentName) {
    this.channels = channels;
    this.dynamoDbClient = dynamoDbClient;
    this.messagesTableName =
        null != serverlessEnvironmentName
            ? serverlessEnvironmentName + '.' + MESSAGES_TABLE
            : MESSAGES_TABLE;
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
  public DynamoDbHistoryMessage find(Member member, String messageId) {
    String id = DynamoDbHistoryMessage.buildId(member.getChannelName(), member.getId());
    Key messageKey = Key.builder().partitionValue(id).sortValue(messageId).build();

    DynamoDbHistoryMessage message = this.messageTable.getItem(messageKey);
    if (message == null) throw new NotFoundException("History Message Not Found");

    return message;
  }

  /**
   * - if lastMessageId == null && lastMessageTime == null - returns all item collection - if limit
   * == null - return all items up to 1 MB - if both lastMessageId and lastActiveTime are provided -
   * lastActiveTime has priority - if connectionUri is provided - lastMessageTime will be for this
   * specific connection - if there is no messagesOwner provided, owner will be found by their
   * connectionUri
   */
  @Override
  public List<HistoryMessage> findAll(MessagesRequest request) {
    String connectionUri = request.getConnectionUri();
    Member member = request.getMessagesOwner();
    Instant lastMessageTime = request.getLastMessageTime();
    String lastMessageId = request.getLastMessageId();
    Integer limit = request.getLimit();

    if (connectionUri == null && member == null)
      throw new IllegalStateException(
          "Member and Connection are not provided, must be at least one of them");

    if (member == null) {
      member = this.channels.find(connectionUri);
    }
    if (connectionUri != null) {
      DynamoDbMember dbMember = (DynamoDbMember) member;
      lastMessageTime = dbMember.getLastMessageTimeForConnection(connectionUri);
    }

    String id = DynamoDbHistoryMessage.buildId(member.getChannelName(), member.getId());
    String keyCondition = "#id = :id ";

    Map<String, String> names = new HashMap<>();
    names.put("#id", MESSAGES_ID_ATTRIBUTE);
    Map<String, AttributeValue> values = new HashMap<>();
    values.put(":id", AttributeValue.fromS(id));

    if (lastMessageTime != null) {
      keyCondition = keyCondition.concat("AND #time > :time");
      names.put("#time", MESSAGES_TIME_ATTRIBUTE);
      values.put(":time", AttributeValue.fromS(lastMessageTime.toString()));
    } else if (lastMessageId != null && !lastMessageId.isEmpty()) {
      HistoryMessage message = this.find(member, lastMessageId);
      Instant messageTime = message.getTime();
      keyCondition = keyCondition.concat("AND #time > :time");
      names.put("#time", MESSAGES_TIME_ATTRIBUTE);
      values.put(":time", AttributeValue.fromS(messageTime.toString()));
    }

    return this.dynamoDbClient
        .query(
            QueryRequest.builder()
                .tableName(messagesTableName)
                .indexName(MESSAGES_TIME_INDEX)
                .keyConditionExpression(keyCondition)
                .expressionAttributeNames(names)
                .expressionAttributeValues(values)
                .scanIndexForward(false)
                .limit(limit)
                .build())
        .items()
        .stream()
        .map(this::buildMessage)
        .sorted(Comparator.comparing(HistoryMessage::getTime))
        .toList();
  }

  private HistoryMessage buildMessage(Map<String, AttributeValue> map) {
    String historyMessageId = map.get(MESSAGES_ID_ATTRIBUTE).s();
    String messageId = map.get(MESSAGES_MESSAGE_ID_ATTRIBUTE).s();
    String content = map.get(MESSAGES_CONTENT_ATTRIBUTE).s();
    boolean incoming = map.get(MESSAGES_INCOMING_ATTRIBUTE).bool();
    String time = map.get(MESSAGES_TIME_ATTRIBUTE).s();
    return new DynamoDbHistoryMessage(
        historyMessageId, messageId, content, Instant.parse(time), incoming);
  }
}

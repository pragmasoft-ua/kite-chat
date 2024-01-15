/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import ua.com.pragmasoft.k1te.backend.router.domain.ChannelName;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.shared.ConflictException;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;

public class DynamoDbChannels implements Channels {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbChannels.class);
  private static final boolean AI_FEATURE_FLAG = false;
  private static final Integer BATCH_SIZE_LIMIT = 25;

  private final HashMap<String, DynamoDbMember> stash = new HashMap<>(8);

  public static final String MEMBERS = "Members";
  public static final String CHANNELS = "Channels";
  public static final String CONNECTIONS = "Connections";
  public static final String REVERSE_CHANNEL_KEY_PREFIX = "host:";
  static final String CONDITION_FAILED = "ConditionalCheckFailed";

  private static Expression nameNotExistsCondition =
      Expression.builder()
          .expression("attribute_not_exists(#attr)")
          .putExpressionName("#attr", "name") // name is a dynamodb keyword
          .build();

  private final String channelsTableName;
  private final String membersTableName;
  private final String connectionsTableName;
  private final DynamoDbEnhancedAsyncClient enhancedDynamo;
  private final DynamoDbAsyncTable<DynamoDbChannel> channelsTable;
  private final DynamoDbAsyncTable<DynamoDbMember> membersTable;
  private final DynamoDbAsyncTable<DynamoDBConnection> connectionsTable;

  public DynamoDbChannels(
      DynamoDbEnhancedAsyncClient enhancedDynamo, String serverlessEnvironmentName) {
    this.membersTableName =
        null != serverlessEnvironmentName ? serverlessEnvironmentName + '.' + MEMBERS : MEMBERS;
    this.channelsTableName =
        null != serverlessEnvironmentName ? serverlessEnvironmentName + '.' + CHANNELS : CHANNELS;
    this.connectionsTableName =
        null != serverlessEnvironmentName
            ? serverlessEnvironmentName + '.' + CONNECTIONS
            : CONNECTIONS;
    log.info("Environment: {}", serverlessEnvironmentName);

    this.enhancedDynamo = enhancedDynamo;
    this.channelsTable =
        this.enhancedDynamo.table(
            this.channelsTableName, TableSchema.fromClass(DynamoDbChannel.class));
    this.membersTable =
        this.enhancedDynamo.table(
            this.membersTableName, TableSchema.fromClass(DynamoDbMember.class));
    this.connectionsTable =
        this.enhancedDynamo.table(
            this.connectionsTableName, TableSchema.fromClass(DynamoDBConnection.class));
  }

  @Override
  public Member hostChannel(String channel, String memberId, String ownerConnection, String title) {
    ChannelName.validate(channel);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(ownerConnection, "owner connection");
    if (null == title) {
      title = channel;
    }

    DynamoDbMember hostMember = new DynamoDbMember(channel, memberId, title, true, null);
    hostMember.updateConnection(ownerConnection);

    if (AI_FEATURE_FLAG) {
      // TODO: 30.11.2023 AI
    }

    DynamoDBConnection dbConnection = new DynamoDBConnection(ownerConnection, channel, memberId);

    DynamoDbChannel newChannel = new DynamoDbChannel(channel, memberId);

    var putChannel =
        TransactPutItemEnhancedRequest.builder(DynamoDbChannel.class)
            .item(newChannel)
            .conditionExpression(nameNotExistsCondition)
            .build();

    DynamoDbChannel reverseChannel =
        new DynamoDbChannel(REVERSE_CHANNEL_KEY_PREFIX + memberId, channel);

    var putReverseChannel =
        TransactPutItemEnhancedRequest.builder(DynamoDbChannel.class)
            .item(reverseChannel)
            .conditionExpression(nameNotExistsCondition)
            .build();

    try {
      this.enhancedDynamo
          .transactWriteItems(
              tx ->
                  tx.addPutItem(this.channelsTable, putChannel)
                      .addPutItem(this.channelsTable, putReverseChannel)
                      .addPutItem(this.membersTable, hostMember)
                      .addPutItem(this.connectionsTable, dbConnection))
          .join();
      return hostMember;
    } catch (TransactionCanceledException e) {
      var reasons = e.cancellationReasons();
      var reason =
          reasons.get(0).code().equals(CONDITION_FAILED)
              ? "Channel name is already taken"
              : reasons.get(1).code().equals(CONDITION_FAILED)
                  ? "You cannot host more than one channel"
                  : reasons.get(2).message() != null
                      ? reasons.get(2).message()
                      : reasons.get(3).message();
      throw new ConflictException(reason, e);
    }
  }

  @Override
  public Member dropChannel(String memberConnection) {
    Objects.requireNonNull(memberConnection, "member connection");

    DynamoDbMember member = this.find(memberConnection);

    if (!member.isHost()) throw new ValidationException("Only host member can drop its channel");

    String channelName = member.getChannelName();

    Key channelKey = Key.builder().partitionValue(channelName).build();
    Key reverseChannelKey =
        Key.builder().partitionValue(REVERSE_CHANNEL_KEY_PREFIX + member.getId()).build();

    List<DynamoDbMember> members = new ArrayList<>();
    this.membersTable
        .query(
            QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(channelKey))
                .build())
        .items()
        .subscribe(members::add)
        .join();

    List<Key> channelsKeys = List.of(channelKey, reverseChannelKey);

    List<Key> membersKeys =
        members.stream()
            .map(
                dbMember ->
                    Key.builder()
                        .partitionValue(dbMember.getChannelName())
                        .sortValue(dbMember.getId())
                        .build())
            .toList();

    List<Key> connectionsKeys =
        members.stream()
            .map(DynamoDbMember::getConnections)
            .filter(connections -> !connections.isEmpty())
            .flatMap(map -> map.values().stream())
            .filter(connection -> connection.getConnectionUri() != null)
            .map(
                connection -> {
                  String[] arr = connection.getConnectionUri().split(":");
                  return Key.builder().partitionValue(arr[0]).sortValue(arr[1]).build();
                })
            .toList();

    List<BatchWriteItemEnhancedRequest> requests =
        new WriteBatchDivider()
            .deleteDivide(channelsKeys, this.channelsTable)
            .deleteDivide(membersKeys, this.membersTable)
            .deleteDivide(connectionsKeys, this.connectionsTable)
            .requests();

    List<CompletableFuture<BatchWriteResult>> futures =
        requests.stream().map(enhancedDynamo::batchWriteItem).toList();

    CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

    return member;
  }

  @Override
  public Member joinChannel(
      String channelName, String memberId, String memberConnection, String userName) {
    ChannelName.validate(channelName);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(memberConnection, "connection");
    Objects.requireNonNull(userName, "user name");

    Key channelKey = Key.builder().partitionValue(channelName).build();
    DynamoDbChannel channel = this.channelsTable.getItem(channelKey).join();
    if (null == channel) {
      throw new NotFoundException("Channel not found");
    }

    Key memberKey = Key.builder().partitionValue(channelName).sortValue(memberId).build();
    DynamoDbMember maybeMember = this.membersTable.getItem(memberKey).join();
    if (maybeMember != null) {
      //      throw new ValidationException("You are already in this Channel");
      return maybeMember; // In order not to fail the app due to some not done work on Client side.
      // Will be deleted when client is ready.
    }

    final String hostId = channel.getHost();
    DynamoDbMember member = new DynamoDbMember(channelName, memberId, userName, false, hostId);
    member.updateConnection(memberConnection);

    DynamoDBConnection dbConnection =
        new DynamoDBConnection(memberConnection, channelName, memberId);

    WriteBatch putMember =
        WriteBatch.builder(DynamoDbMember.class)
            .addPutItem(member)
            .mappedTableResource(this.membersTable)
            .build();
    WriteBatch putConnection =
        WriteBatch.builder(DynamoDBConnection.class)
            .addPutItem(dbConnection)
            .mappedTableResource(this.connectionsTable)
            .build();
    try {
      this.enhancedDynamo
          .batchWriteItem(builder -> builder.writeBatches(putMember, putConnection))
          .join();
      return member;
    } catch (Exception e) {
      throw new ConflictException(e.getMessage(), e);
    }
  }

  @Override
  public Member reconnect(String channelName, String memberId, String newConnection) {
    ChannelName.validate(channelName);

    Key channelKey = Key.builder().partitionValue(channelName).build();
    DynamoDbChannel channel = this.channelsTable.getItem(channelKey).join();
    if (channel == null)
      throw new NotFoundException("There is no Channel with name " + channelName);

    if (memberId == null || memberId.isEmpty()) return null;

    Key memberKey = Key.builder().partitionValue(channelName).sortValue(memberId).build();
    DynamoDbMember maybeMember = this.membersTable.getItem(memberKey).join();
    if (maybeMember == null)
      throw new NotFoundException(
          "There is no Member with id %s in Channel %s".formatted(memberId, channelName));

    DynamoDBConnection dbConnection = new DynamoDBConnection(newConnection, channelName, memberId);
    maybeMember.updateConnection(newConnection);

    WriteBatch memberRequest =
        WriteBatch.builder(DynamoDbMember.class)
            .addPutItem(maybeMember)
            .mappedTableResource(this.membersTable)
            .build();
    WriteBatch connectionRequest =
        WriteBatch.builder(DynamoDBConnection.class)
            .addPutItem(dbConnection)
            .mappedTableResource(this.connectionsTable)
            .build();

    this.enhancedDynamo
        .batchWriteItem(builder -> builder.writeBatches(memberRequest, connectionRequest))
        .join();
    return maybeMember;
  }

  @Override
  public Member disconnect(String connectionUri) {
    DynamoDbMember member = this.find(connectionUri);
    String connectorId = Connector.connectorId(connectionUri);
    String rawConnection = Connector.rawConnection(connectionUri);
    Key connectionKey = Key.builder().partitionValue(connectorId).sortValue(rawConnection).build();

    this.connectionsTable.deleteItem(connectionKey).join();
    member.deleteConnection(connectionUri); // Member is updated via flush()
    return member;
  }

  @Override
  public Member leaveChannel(String memberConnection) {
    Objects.requireNonNull(memberConnection, "member connection");

    DynamoDbMember member = this.find(memberConnection);
    if (member.isHost()) {
      throw new ValidationException("Host member cannot leave channel. You can only drop it");
    }
    String connectorId = Connector.connectorId(memberConnection);
    String rawConnection = Connector.rawConnection(memberConnection);
    Key connectionKey = Key.builder().partitionValue(connectorId).sortValue(rawConnection).build();
    Key memberKey =
        Key.builder().partitionValue(member.getChannelName()).sortValue(member.getId()).build();

    WriteBatch deleteMember =
        WriteBatch.builder(DynamoDbMember.class)
            .addDeleteItem(memberKey)
            .mappedTableResource(this.membersTable)
            .build();
    WriteBatch deleteConnection =
        WriteBatch.builder(DynamoDBConnection.class)
            .addDeleteItem(connectionKey)
            .mappedTableResource(this.connectionsTable)
            .build();
    try {
      this.enhancedDynamo
          .batchWriteItem(builder -> builder.writeBatches(deleteMember, deleteConnection))
          .join();
      return member;
    } catch (Exception e) { // TransactionCanceledException
      throw new KiteException(e.getMessage(), e);
    }
  }

  @Override
  public DynamoDbMember find(String channel, String id) {
    DynamoDbMember cachedMember = this.stash.get(this.constructCachedMemberId(channel, id));
    if (cachedMember != null) return cachedMember;

    Key memberKey = Key.builder().partitionValue(channel).sortValue(id).build();
    DynamoDbMember member = this.membersTable.getItem(memberKey).join();
    if (null == member) {
      throw new NotFoundException("Not found member");
    }

    this.stash.put(constructCachedMemberId(channel, id), member);
    return member;
  }

  @Override
  public DynamoDbMember find(String memberConnection) {
    Objects.requireNonNull(memberConnection, "connection");

    String connectorId = Connector.connectorId(memberConnection);
    String rawConnection = Connector.rawConnection(memberConnection);

    Key connectionKey = Key.builder().partitionValue(connectorId).sortValue(rawConnection).build();
    DynamoDBConnection dbConnection = this.connectionsTable.getItem(connectionKey).join();
    if (dbConnection == null) throw new NotFoundException("Searched connection Not Found");

    String channelName = dbConnection.getChannelName();
    String memberId = dbConnection.getMemberId();

    return find(channelName, memberId);
  }

  @Override
  public String findUnAnsweredMessage(Member from, Member to) {
    DynamoDbMember member = (DynamoDbMember) from;
    Map<String, String> pinnedMessages = member.getPinnedMessages();
    return pinnedMessages.get(to.getId());
  }

  public Member switchConnection(String channelName, String memberId, String newConnection) {
    DynamoDbMember member = find(channelName, memberId);

    member.updateConnection(newConnection);
    DynamoDBConnection dbConnection = new DynamoDBConnection(newConnection, channelName, memberId);

    this.connectionsTable.putItem(dbConnection).join();
    // Member is updated via flush()
    return member;
  }

  public void flush() {
    List<DynamoDbMember> cachedMembers =
        this.stash.values().stream().filter(DynamoDbMember::isDirty).toList();

    if (!cachedMembers.isEmpty()) {
      List<WriteBatch> writeBatches =
          cachedMembers.stream()
              .map(
                  member ->
                      WriteBatch.builder(DynamoDbMember.class)
                          .mappedTableResource(this.membersTable)
                          .addPutItem(member)
                          .build())
              .toList();
      this.enhancedDynamo.batchWriteItem(builder -> builder.writeBatches(writeBatches)).join();
      this.stash.clear();
    }
    log.debug("{} flush", this.getClass().getSimpleName());
  }

  private String constructCachedMemberId(String channelName, String memberId) {
    Objects.requireNonNull(channelName);
    Objects.requireNonNull(memberId);
    return channelName + "::" + memberId;
  }
}

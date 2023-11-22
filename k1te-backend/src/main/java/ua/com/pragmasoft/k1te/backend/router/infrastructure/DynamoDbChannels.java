/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.CancellationReason;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
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

  private static Expression pkAndSkNotExistCondition =
      Expression.builder()
          .expression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
          .expressionNames(
              Map.of(
                  "#pk", "ChannelName",
                  "#sk", "id"))
          .build();

  private static final Expression pkAndSkExistCondition =
      Expression.builder()
          .expression("attribute_exists(#pk) AND attribute_exists(#sk)")
          .expressionNames(
              Map.of(
                  "#pk", "channelName",
                  "#sk", "id"))
          .build();

  private final String channelsTableName;
  private final String membersTableName;
  private final String connectionsTableName;
  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbTable<DynamoDbChannel> channelsTable;
  private final DynamoDbTable<DynamoDbMember> membersTable;
  private final DynamoDbTable<DynamoDBConnection> connectionsTable;

  public DynamoDbChannels(DynamoDbEnhancedClient enhancedDynamo, String serverlessEnvironmentName) {
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
    String connectorId = Connector.connectorId(ownerConnection);
    String rawConnection = Connector.rawConnection(ownerConnection);

    DynamoDbMember hostMember =
        new DynamoDbMember.DynamoDbMemberBuilder()
            .withId(memberId)
            .withChannelName(channel)
            .withUserName(title)
            .withHost(true)
            .build();
    hostMember.updateConnectionUri(connectorId, rawConnection);

    if (AI_FEATURE_FLAG) {
      hostMember.setAiUri("Ai URI");
    }

    DynamoDBConnection dbConnection =
        new DynamoDBConnection(connectorId, rawConnection, channel, memberId);

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
      this.enhancedDynamo.transactWriteItems(
          tx ->
              tx.addPutItem(this.channelsTable, putChannel)
                  .addPutItem(this.channelsTable, putReverseChannel)
                  .addPutItem(this.membersTable, hostMember)
                  .addPutItem(this.connectionsTable, dbConnection));
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
    String connectorId = Connector.connectorId(memberConnection);
    String rawConnection = Connector.rawConnection(memberConnection);

    Key channelKey = Key.builder().partitionValue(channelName).build();
    Key reverseChannelKey =
        Key.builder().partitionValue(REVERSE_CHANNEL_KEY_PREFIX + member.getId()).build();
    Key connectionKey = Key.builder().partitionValue(connectorId).sortValue(rawConnection).build();

    TransactWriteItemsEnhancedRequest.Builder builder = TransactWriteItemsEnhancedRequest.builder();
    this.membersTable
        .query(query -> query.queryConditional(QueryConditional.keyEqualTo(channelKey)))
        .items()
        .forEach(
            dynamoDbMember ->
                builder.addDeleteItem(
                    this.membersTable, dynamoDbMember)); // Deletes all members in channel

    this.enhancedDynamo.transactWriteItems(
        builder
            .addDeleteItem(this.channelsTable, channelKey)
            .addDeleteItem(this.channelsTable, reverseChannelKey)
            .addDeleteItem(this.connectionsTable, connectionKey)
            .build());

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
    DynamoDbChannel channel = this.channelsTable.getItem(channelKey);
    if (null == channel) {
      throw new NotFoundException("Channel not found");
    }
    String connectorId = Connector.connectorId(memberConnection);
    String rawConnection = Connector.rawConnection(memberConnection);

    Key memberKey = Key.builder().partitionValue(channelName).sortValue(memberId).build();
    DynamoDbMember maybeMember = this.membersTable.getItem(memberKey);
    if (maybeMember != null) {
      if (maybeMember.hasConnection(memberConnection)) {
        throw new ValidationException("You are already in this Channel");
      }
      DynamoDBConnection dbConnection =
          new DynamoDBConnection(connectorId, rawConnection, channelName, memberId);
      maybeMember.updateConnectionUri(connectorId, rawConnection);
      try {
        this.enhancedDynamo.transactWriteItems(
            tx ->
                tx.addUpdateItem(this.membersTable, maybeMember)
                    .addPutItem(this.connectionsTable, dbConnection));
        return maybeMember;
      } catch (Exception e) {
        throw new KiteException(e.getMessage(), e);
      }
    }

    final String hostId = channel.getHost();
    DynamoDbMember member =
        new DynamoDbMember.DynamoDbMemberBuilder()
            .withChannelName(channelName)
            .withId(memberId)
            .withUserName(userName)
            .withHost(false)
            .withPeerMemberId(hostId)
            .build();
    member.updateConnectionUri(connectorId, rawConnection);
    DynamoDBConnection dbConnection =
        new DynamoDBConnection(connectorId, rawConnection, channelName, memberId);

    var putMemberRequest =
        TransactPutItemEnhancedRequest.builder(DynamoDbMember.class)
            .item(member)
            .conditionExpression(pkAndSkNotExistCondition)
            .build();
    try {
      this.enhancedDynamo.transactWriteItems(
          tx ->
              tx.addPutItem(this.membersTable, putMemberRequest)
                  .addPutItem(this.connectionsTable, dbConnection));
      return member;
    } catch (TransactionCanceledException e) {
      List<CancellationReason> reasons = e.cancellationReasons();
      String reason =
          reasons.get(0).code().equals(CONDITION_FAILED)
              ? "You can't /join the same Channel"
              : reasons.get(1).message();
      throw new ConflictException(reason, e);
    }
  }

  // TODO: 20.11.2023 Should delete only connection so that Member could reconnect
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

    if (!member.getConnectionUri().equals(memberConnection)) {
      member.deleteConnection(connectorId);
      this.enhancedDynamo.transactWriteItems(
          tx ->
              tx.addDeleteItem(
                      this.connectionsTable,
                      connectionKey) // Deletes only connection because it's not the most recent one
                  .addUpdateItem(this.membersTable, member));
      return member;
    }

    try {
      this.enhancedDynamo.transactWriteItems(
          tx ->
              tx.addDeleteItem(this.connectionsTable, connectionKey)
                  .addDeleteItem(this.membersTable, member));
    } catch (Exception e) { // TransactionCanceledException
      throw new KiteException(e.getMessage(), e);
    }

    return member;
  }

  @Override
  public DynamoDbMember find(String channel, String id) {
    Key memberKey = Key.builder().partitionValue(channel).sortValue(id).build();
    DynamoDbMember member = this.membersTable.getItem(memberKey);
    if (null == member) {
      throw new NotFoundException("Not found member");
    }
    return member;
  }

  @Override
  public Member findHost(String channelName) {
    Objects.requireNonNull(channelName);
    ChannelName.validate(channelName);

    Key channelKey = Key.builder().partitionValue(channelName).build();
    DynamoDbChannel channel = this.channelsTable.getItem(channelKey);
    if (channel == null)
      throw new NotFoundException("Channel has not been found by a given channelName");

    return find(channelName, channel.getHost());
  }

  @Override
  public DynamoDbMember find(String memberConnection) {
    Objects.requireNonNull(memberConnection, "connection");

    String connectorId = Connector.connectorId(memberConnection);
    String rawConnection = Connector.rawConnection(memberConnection);

    Key connectionKey = Key.builder().partitionValue(connectorId).sortValue(rawConnection).build();
    DynamoDBConnection dbConnection = this.connectionsTable.getItem(connectionKey);
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

    String connectorId = Connector.connectorId(newConnection);
    String rawConnection = Connector.rawConnection(newConnection);

    member.updateConnectionUri(connectorId, rawConnection);

    DynamoDBConnection dbConnection =
        new DynamoDBConnection(connectorId, rawConnection, channelName, memberId);

    try {
      this.enhancedDynamo.transactWriteItems(
          tx -> tx.addUpdateItem(membersTable, member).addPutItem(connectionsTable, dbConnection));
    } catch (TransactionCanceledException e) {
      throw new KiteException(e.getMessage(), e);
    }
    return member;
  }

  @Override
  public void updatePeer(Member myMember, String peerMember) {
    Objects.requireNonNull(peerMember, "peer Member");
    if (peerMember.equals(myMember.getPeerMemberId())) {
      return;
    }
    DynamoDbMember member = (DynamoDbMember) myMember;
    member.setPeerMemberId(peerMember);

    this.updateMemberIfExist(member);
  }

  @Override
  public void updateConnection(
      Member memberToUpdate, String connectionUri, String messageId, Instant usageTime) {
    Objects.requireNonNull(connectionUri);
    Objects.requireNonNull(messageId);
    Objects.requireNonNull(usageTime);

    String connectorId = Connector.connectorId(connectionUri);
    String rawConnection = Connector.rawConnection(connectionUri);

    DynamoDbMember member = (DynamoDbMember) memberToUpdate;

    member.updateConnectionUri(connectorId, rawConnection, messageId, usageTime);
    this.updateMemberIfExist(member);
  }

  @Override
  public void updateUnAnsweredMessage(Member from, Member to, String pinnedMessagedId) {
    DynamoDbMember member = (DynamoDbMember) from;
    member.addPinnedMessage(to.getId(), pinnedMessagedId);

    this.updateMemberIfExist(member);
  }

  @Override
  public void deleteUnAnsweredMessage(Member from, Member to) {
    DynamoDbMember member = (DynamoDbMember) from;
    member.deletePinnedMessage(to.getId());

    this.updateMemberIfExist(member);
  }

  private void updateMemberIfExist(DynamoDbMember member) {
    var updateRequest =
        UpdateItemEnhancedRequest.builder(DynamoDbMember.class)
            .item(member)
            .conditionExpression(pkAndSkExistCondition)
            .build();
    try {
      this.membersTable.updateItem(updateRequest);
    } catch (ConditionalCheckFailedException conditionalException) {
      log.warn("Member has already left the Channel");
    } catch (Exception e) {
      throw new ValidationException(e.getMessage(), e);
    }
  }
}

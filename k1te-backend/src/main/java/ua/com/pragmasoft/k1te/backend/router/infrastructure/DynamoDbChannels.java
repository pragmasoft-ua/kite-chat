/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

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
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
import ua.com.pragmasoft.k1te.backend.router.domain.ChannelName;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.shared.ConflictException;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;

public class DynamoDbChannels implements Channels {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbChannels.class);

  public static final String MEMBERS = "Members";
  public static final String CHANNELS = "Channels";
  public static final String REVERSE_CHANNEL_KEY_PREFIX = "host:";
  static final String CONDITION_FAILED = "ConditionalCheckFailed";

  private static Expression nameNotExistsCondition =
      Expression.builder()
          .expression("attribute_not_exists(#attr)")
          .putExpressionName("#attr", "name") // name is a dynamodb keyword
          .build();

  private static Expression pkAndSkNotExistsCondition =
      Expression.builder()
          .expression("attribute_not_exists(#pk) AND attribute_not_exists(#sk)")
          .expressionNames(
              Map.of(
                  "#pk", "ChannelName",
                  "#sk", "id"))
          .build();

  private final String channelsTableName;
  private final String membersTableName;
  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbTable<DynamoDbChannel> channelsTable;
  private final DynamoDbTable<DynamoDbMember> membersTable;

  public DynamoDbChannels(DynamoDbEnhancedClient enhancedDynamo, String serverlessEnvironmentName) {
    this.membersTableName =
        null != serverlessEnvironmentName ? serverlessEnvironmentName + '.' + MEMBERS : MEMBERS;
    this.channelsTableName =
        null != serverlessEnvironmentName ? serverlessEnvironmentName + '.' + CHANNELS : CHANNELS;
    log.info("Environment: {}", serverlessEnvironmentName);
    this.enhancedDynamo = enhancedDynamo;
    this.channelsTable =
        this.enhancedDynamo.table(
            this.channelsTableName, TableSchema.fromClass(DynamoDbChannel.class));
    this.membersTable =
        this.enhancedDynamo.table(
            this.membersTableName, TableSchema.fromClass(DynamoDbMember.class));
  }

  @Override
  public Member hostChannel(String channel, String memberId, String ownerConnection, String title) {

    ChannelName.validate(channel);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(ownerConnection, "owner connection");
    if (null == title) {
      title = channel;
    }

    DynamoDbMember hostMember =
        new DynamoDbMember(memberId, channel, title, ownerConnection, true, null);

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
                  .addPutItem(this.membersTable, hostMember));
      return hostMember;
    } catch (TransactionCanceledException e) {
      var reasons = e.cancellationReasons();
      var reason =
          reasons.get(0).code().equals(CONDITION_FAILED)
              ? "Channel name is already taken"
              : reasons.get(1).code().equals(CONDITION_FAILED)
                  ? "You cannot host more than one channel"
                  : reasons.get(2).message();
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

    this.enhancedDynamo.transactWriteItems(
        TransactWriteItemsEnhancedRequest.builder()
            .addDeleteItem(this.channelsTable, channelKey)
            .addDeleteItem(this.channelsTable, reverseChannelKey)
            .addDeleteItem(this.membersTable, member)
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
    final String hostId = channel.getHost();
    DynamoDbMember clientMember =
        new DynamoDbMember(memberId, channelName, userName, memberConnection, false, hostId);

    var putMemberRequest =
        PutItemEnhancedRequest.builder(DynamoDbMember.class)
            .item(clientMember)
            .conditionExpression(pkAndSkNotExistsCondition)
            .build();
    try {
      this.membersTable.putItem(putMemberRequest);
      return clientMember;
    } catch (ConditionalCheckFailedException e) {
      throw new KiteException("You can't /join the same Channel", e);
    }
  }

  @Override
  public Member leaveChannel(String memberConnection) {

    Objects.requireNonNull(memberConnection, "member connection");

    DynamoDbMember member = this.find(memberConnection);

    if (member.isHost()) {
      throw new ValidationException("Host member cannot leave channel. You can only drop it");
    }

    try {
      this.membersTable.deleteItem(member);
    } catch (Exception e) { // TransactionCanceledException
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
    try {
      DynamoDbMember member = (DynamoDbMember) myMember;

      member.setPeerMemberId(peerMember);
      this.membersTable.updateItem(member);
    } catch (Exception e) {
      throw new ValidationException(e.getMessage(), e);
    }
  }

  @Override
  public DynamoDbMember find(String memberConnection) {
    Key secondaryKey = Key.builder().partitionValue(memberConnection).build();
    QueryConditional qc = QueryConditional.keyEqualTo(secondaryKey);
    QueryEnhancedRequest q = QueryEnhancedRequest.builder().queryConditional(qc).limit(1).build();
    var secondaryIndex = this.membersTable.index(DynamoDbMember.BY_CONNECTION);
    var response = secondaryIndex.query(q);
    if (!response.iterator().hasNext()) {
      throw new NotFoundException();
    }
    var page = response.iterator().next();
    if (page.items().isEmpty()) {
      throw new NotFoundException();
    }
    return page.items().get(0);
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
}

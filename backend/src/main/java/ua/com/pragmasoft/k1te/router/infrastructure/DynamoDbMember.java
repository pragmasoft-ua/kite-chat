package ua.com.pragmasoft.k1te.router.infrastructure;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnoreNulls;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import ua.com.pragmasoft.k1te.router.domain.Member;

@DynamoDbBean
public class DynamoDbMember implements Member {

  static final String BY_CONNECTION = "ByConnection";

  String id;
  String channelName;
  String connectionUri;
  String userName;
  boolean host;
  String peerMemberId;

  DynamoDbMember(String id, String channelName, String userName, String connectionUri, boolean host,
      String peerMemberId) {
    super();
    this.id = id;
    this.channelName = channelName;
    this.userName = userName;
    this.connectionUri = connectionUri;
    this.host = host;
    this.peerMemberId = peerMemberId;
  }

  public DynamoDbMember() {
    super();
  }

  @Override
  @DynamoDbSortKey
  public String getId() {
    return id;
  }

  void setId(String id) {
    this.id = id;
  }

  @Override
  @DynamoDbPartitionKey
  public String getChannelName() {
    return channelName;
  }

  void setChannelName(String channel) {
    this.channelName = channel;
  }

  @Override
  public String getUserName() {
    return userName;
  }

  void setUserName(String userName) {
    this.userName = userName;
  }

  @Override
  public boolean isHost() {
    return host;
  }

  void setHost(boolean host) {
    this.host = host;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getPeerMemberId() {
    return peerMemberId;
  }

  void setPeerMemberId(String peerConnectionId) {
    this.peerMemberId = peerConnectionId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = BY_CONNECTION)
  @DynamoDbIgnoreNulls
  @Override
  public String getConnectionUri() {
    return this.connectionUri;
  }

  void setConnectionUri(String connection) {
    this.connectionUri = connection;
  }

  @Override
  public String toString() {
    return "DynamoDbMember [id=" + id + ", channelName=" + channelName + ", connection="
        + connectionUri + ", userName=" + userName + ", host=" + host + ", peerMemberId="
        + peerMemberId + "]";
  }

}

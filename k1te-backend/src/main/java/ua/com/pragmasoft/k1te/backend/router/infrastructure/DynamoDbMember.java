/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnoreNulls;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;

@DynamoDbBean
public class DynamoDbMember implements Member {

  static final String BY_CONNECTION = "ByConnection";

  private String id;
  private String channelName;
  private String connectionUri;
  private String userName;
  private boolean host;
  private String peerMemberId;

  public DynamoDbMember(
      String id,
      String channelName,
      String userName,
      String connectionUri,
      boolean host,
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

  public void setId(String id) {
    this.id = id;
  }

  @Override
  @DynamoDbPartitionKey
  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channel) {
    this.channelName = channel;
  }

  @Override
  public String getUserName() {
    return userName;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  @Override
  public boolean isHost() {
    return host;
  }

  public void setHost(boolean host) {
    this.host = host;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getPeerMemberId() {
    return peerMemberId;
  }

  public void setPeerMemberId(String peerConnectionId) {
    this.peerMemberId = peerConnectionId;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = BY_CONNECTION)
  @DynamoDbIgnoreNulls
  @Override
  public String getConnectionUri() {
    return this.connectionUri;
  }

  public void setConnectionUri(String connection) {
    this.connectionUri = connection;
  }

  @Override
  public String toString() {
    return "DynamoDbMember [id="
        + id
        + ", channelName="
        + channelName
        + ", connection="
        + connectionUri
        + ", userName="
        + userName
        + ", host="
        + host
        + ", peerMemberId="
        + peerMemberId
        + "]";
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;

@DynamoDbBean
public class DynamoDbMember implements Member {
  private String channelName;
  private String id;
  private String userName;
  private boolean host;
  private String peerMemberId;
  private String lastActiveConnector;
  private Map<String, MemberConnection> connections = new HashMap<>();
  private Map<String, String> pinnedMessages = new HashMap<>();
  private boolean dirty = false;

  public DynamoDbMember() {}

  public DynamoDbMember(
      String channelName,
      String id,
      String userName,
      boolean host,
      String peerMemberId,
      String lastActiveConnector,
      Map<String, MemberConnection> connections,
      Map<String, String> pinnedMessages,
      boolean dirty) {
    this.channelName = channelName;
    this.id = id;
    this.userName = userName;
    this.host = host;
    this.peerMemberId = peerMemberId;
    this.lastActiveConnector = lastActiveConnector;
    this.connections = connections;
    this.pinnedMessages = pinnedMessages;
    this.dirty = dirty;
  }

  public DynamoDbMember(
      String channelName, String id, String userName, boolean host, String peerMemberId) {
    this.channelName = channelName;
    this.id = id;
    this.userName = userName;
    this.host = host;
    this.peerMemberId = peerMemberId;
  }

  /** Returns the most relevant connection by comparing lastActiveTime attribute */
  @Override
  public String getConnectionUri() {
    if (this.lastActiveConnector != null && !this.lastActiveConnector.isEmpty()) {
      MemberConnection memberConnection = this.connections.get(this.lastActiveConnector);
      if (memberConnection == null)
        throw new IllegalStateException(
            "Member doesn't have any connection with a given connectorId "
                + this.lastActiveConnector);
      return memberConnection.getConnectionUri();
    } else {
      return this.connections.values().stream()
          .map(MemberConnection::getConnectionUri)
          .filter(Objects::nonNull)
          .findFirst()
          .orElse(null);
    }
  }

  @Override
  public void updatePeer(String peerMemberId) {
    if (peerMemberId.equals(this.getPeerMemberId())) {
      return;
    }
    this.setPeerMemberId(peerMemberId);
    this.setDirty(true);
  }

  @Override
  public void updateUnAnsweredMessage(Member toMember, String messageId) {
    this.pinnedMessages.put(toMember.getId(), messageId);
    this.setDirty(true);
  }

  @Override
  public void deleteUnAnsweredMessage(Member toMember) {
    this.pinnedMessages.remove(toMember.getId());
    this.setDirty(true);
  }

  public void updateConnection(String connectionUri) {
    this.updateConnection(connectionUri, null, null);
  }

  @Override
  public void updateConnection(String connectionUri, String messageId, Instant lastMessageTime) {
    Objects.requireNonNull(connectionUri);

    String connectorId = Connector.connectorId(connectionUri);
    MemberConnection memberConnection = this.connections.get(connectorId);
    if (memberConnection != null) {
      if (messageId != null) {
        memberConnection.setLastMessageId(messageId);
      }
      if (lastMessageTime != null) {
        memberConnection.setLastMessageTime(lastMessageTime);
      }
      memberConnection.setConnectionUri(connectionUri);
    } else {
      MemberConnection connection = new MemberConnection(connectionUri, messageId, lastMessageTime);
      this.connections.put(connectorId, connection);
    }

    this.lastActiveConnector = connectorId;
    this.setDirty(true);
  }

  public void deleteConnection(String connectionUri) {
    String connectorId = Connector.connectorId(connectionUri);
    MemberConnection memberConnection = this.connections.get(connectorId);
    if (memberConnection == null)
      throw new IllegalStateException(
          "There is no connection by a given connectorId " + connectorId);

    memberConnection.setConnectionUri(null);
    if (this.lastActiveConnector.equals(connectorId)) {
      this.lastActiveConnector = null;
    }
    this.setDirty(true);
  }

  public Instant getLastMessageTimeForConnection(String connectionUri) {
    String connectorId = Connector.connectorId(connectionUri);
    MemberConnection memberConnection = this.connections.get(connectorId);
    return memberConnection != null ? memberConnection.getLastMessageTime() : null;
  }

  @Override
  @DynamoDbPartitionKey
  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
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

  public void setPeerMemberId(String peerMemberId) {
    this.peerMemberId = peerMemberId;
  }

  @DynamoDbIgnoreNulls
  public String getLastActiveConnector() {
    return lastActiveConnector;
  }

  public void setLastActiveConnector(String lastActiveConnector) {
    this.lastActiveConnector = lastActiveConnector;
  }

  @DynamoDbIgnoreNulls
  public Map<String, MemberConnection> getConnections() {
    return connections;
  }

  public void setConnections(Map<String, MemberConnection> connections) {
    this.connections = connections;
  }

  @DynamoDbIgnoreNulls
  public Map<String, String> getPinnedMessages() {
    return pinnedMessages;
  }

  public void setPinnedMessages(Map<String, String> pinnedMessages) {
    this.pinnedMessages = pinnedMessages;
  }

  @DynamoDbIgnore
  public boolean isDirty() {
    return dirty;
  }

  public void setDirty(boolean dirty) {
    this.dirty = dirty;
  }

  @Override
  public String toString() {
    return "DynamoDbMember{"
        + "channelName='"
        + channelName
        + '\''
        + ", id='"
        + id
        + '\''
        + ", userName='"
        + userName
        + '\''
        + ", host="
        + host
        + ", peerMemberId='"
        + peerMemberId
        + '\''
        + ", lastActiveConnector='"
        + lastActiveConnector
        + '\''
        + ", connections="
        + connections
        + ", pinnedMessages="
        + pinnedMessages
        + ", dirty="
        + dirty
        + '}';
  }

  @DynamoDbBean
  public static class MemberConnection {
    private String connectionUri;
    private String lastMessageId;
    private Instant lastMessageTime;

    public MemberConnection(String connectionUri, String lastMessageId, Instant lastMessageTime) {
      this.connectionUri = connectionUri;
      this.lastMessageId = lastMessageId;
      this.lastMessageTime = lastMessageTime;
    }

    public MemberConnection() {}

    public String getConnectionUri() {
      return connectionUri;
    }

    public void setConnectionUri(String connectionUri) {
      this.connectionUri = connectionUri;
    }

    public String getLastMessageId() {
      return lastMessageId;
    }

    public void setLastMessageId(String lastMessageId) {
      this.lastMessageId = lastMessageId;
    }

    public Instant getLastMessageTime() {
      return lastMessageTime;
    }

    public void setLastMessageTime(Instant lastMessageTime) {
      this.lastMessageTime = lastMessageTime;
    }

    @Override
    public String toString() {
      return "MemberConnection{"
          + "connection='"
          + connectionUri
          + '\''
          + ", lastMessageId='"
          + lastMessageId
          + '\''
          + ", lastMessageTime="
          + lastMessageTime
          + '}';
    }
  }
}

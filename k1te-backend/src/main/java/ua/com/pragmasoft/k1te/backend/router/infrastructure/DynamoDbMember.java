/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;

@DynamoDbBean
public class DynamoDbMember implements Member {

  private String id;
  private String channelName;
  private String tgUri;
  private Instant tgLastActiveTime;
  private Instant tgLastMessageTime;
  private String tgLastMessageId;
  private String wsUri;
  private Instant wsLastActiveTime;
  private Instant wsLastMessageTime;
  private String wsLastMessageId;
  private String aiUri;
  private Instant aiLastActiveTime;
  private Instant aiLastMessageTime;
  private String aiLastMessageId;
  private String userName;
  private boolean host;
  private String peerMemberId;
  private Map<String, String> pinnedMessages = new HashMap<>();

  public DynamoDbMember(
      String id,
      String channelName,
      String tgUri,
      Instant tgLastActiveTime,
      Instant tgLastMessageTime,
      String tgLastMessageId,
      String wsUri,
      Instant wsLastActiveTime,
      Instant wsLastMessageTime,
      String wsLastMessageId,
      String aiUri,
      Instant aiLastActiveTime,
      Instant aiLastMessageTime,
      String aiLastMessageId,
      String userName,
      boolean host,
      String peerMemberId,
      Map<String, String> pinnedMessageId) {
    this.id = id;
    this.channelName = channelName;
    this.tgUri = tgUri;
    this.tgLastActiveTime = tgLastActiveTime;
    this.tgLastMessageTime = tgLastMessageTime;
    this.tgLastMessageId = tgLastMessageId;
    this.wsUri = wsUri;
    this.wsLastActiveTime = wsLastActiveTime;
    this.wsLastMessageTime = wsLastMessageTime;
    this.wsLastMessageId = wsLastMessageId;
    this.aiUri = aiUri;
    this.aiLastActiveTime = aiLastActiveTime;
    this.aiLastMessageTime = aiLastMessageTime;
    this.aiLastMessageId = aiLastMessageId;
    this.userName = userName;
    this.host = host;
    this.peerMemberId = peerMemberId;
    this.pinnedMessages = pinnedMessageId;
  }

  public DynamoDbMember() {
    super();
  }

  /** Returns the most relevant connection by comparing lastActiveTime attribute */
  @Override
  public String getConnectionUri() {
    String maybeTgUri = this.tgUri != null ? TelegramConnector.TG + ":" + this.tgUri : null;
    String maybeWsUri = this.wsUri != null ? WsConnector.WS + ":" + this.wsUri : null;
    String maybeAiUri = this.aiUri != null ? "ai:" + this.aiUri : null;

    String[] uris = {maybeTgUri, maybeWsUri, maybeAiUri};
    Instant[] lastTimes = {this.tgLastActiveTime, this.wsLastActiveTime, this.aiLastActiveTime};

    String connectionUri = null;
    Instant mostRecentTime = null;

    for (int i = 0; i < uris.length; i++) {
      if (uris[i] != null && (mostRecentTime == null || lastTimes[i].isAfter(mostRecentTime))) {
        connectionUri = uris[i];
        mostRecentTime = lastTimes[i];
      }
    }

    return connectionUri;
  }

  @Override
  public void updatePeer(String peerMemberId) {
    if (peerMemberId.equals(this.getPeerMemberId())) {
      return;
    }
    this.setPeerMemberId(peerMemberId);
  }

  @Override
  public void updateUnAnsweredMessage(Member toMember, String messageId) {
    this.pinnedMessages.put(toMember.getId(), messageId);
  }

  @Override
  public void deleteUnAnsweredMessage(Member toMember) {
    this.pinnedMessages.remove(toMember.getId());
  }

  public void updateConnection(String connectionUri) {
    this.updateConnection(connectionUri, null, null);
  }

  @Override
  public void updateConnection(String connectionUri, String messageId, Instant usageTime) {
    String connectorId = Connector.connectorId(connectionUri);
    String rawConnection = Connector.rawConnection(connectionUri);
    Instant activeTime = usageTime != null ? usageTime : Instant.now();
    switch (connectorId) {
      case (TelegramConnector.TG) -> {
        this.setTgUri(rawConnection);
        if (messageId != null) this.setTgLastMessageId(messageId);
        if (usageTime != null) this.setTgLastMessageTime(usageTime);
        this.setTgLastActiveTime(activeTime);
      }
      case (WsConnector.WS) -> {
        this.setWsUri(rawConnection);
        if (messageId != null) this.setWsLastMessageId(messageId);
        if (usageTime != null) this.setWsLastMessageTime(usageTime);
        this.setWsLastActiveTime(activeTime);
      }
      case ("ai") -> {
        this.setAiUri(rawConnection);
        if (messageId != null) this.setAiLastMessageId(messageId);
        if (usageTime != null) this.setAiLastMessageTime(usageTime);
        this.setAiLastActiveTime(activeTime);
      }
      default -> throw new IllegalStateException("Unsupported connector id");
    }
  }

  public void deleteConnection(String connectorId) {
    switch (connectorId) {
      case (TelegramConnector.TG) -> {
        this.setTgUri(null);
        this.setTgLastActiveTime(null);
      }
      case (WsConnector.WS) -> {
        this.setWsUri(null);
        this.setWsLastActiveTime(null);
      }
      case ("ai") -> {
        this.setAiUri(null);
        this.setAiLastActiveTime(null);
      }
      default -> throw new IllegalStateException("Unsupported connector id");
    }
  }

  public Instant getLastMessageTimeForConnection(String connectionUri) {
    String connectorId = Connector.connectorId(connectionUri);
    return switch (connectorId) {
      case (TelegramConnector.TG) -> this.getTgLastMessageTime();
      case (WsConnector.WS) -> this.getWsLastMessageTime();
      case ("ai") -> this.getAiLastMessageTime();
      default -> null;
    };
  }

  public boolean hasConnection(String connection) {
    return (this.tgUri != null && this.tgUri.equals(connection))
        || (this.wsUri != null && this.wsUri.equals(connection))
        || (this.aiUri != null && this.aiUri.equals(connection));
  }

  @Override
  @DynamoDbSortKey
  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  @DynamoDbIgnoreNulls
  public Instant getTgLastActiveTime() {
    return tgLastActiveTime;
  }

  public void setTgLastActiveTime(Instant tgLastActiveTime) {
    this.tgLastActiveTime = tgLastActiveTime;
  }

  @DynamoDbIgnoreNulls
  public Instant getWsLastActiveTime() {
    return wsLastActiveTime;
  }

  public void setWsLastActiveTime(Instant wsLastActiveTime) {
    this.wsLastActiveTime = wsLastActiveTime;
  }

  @DynamoDbIgnoreNulls
  public Instant getAiLastActiveTime() {
    return aiLastActiveTime;
  }

  public void setAiLastActiveTime(Instant aiLastActiveTime) {
    this.aiLastActiveTime = aiLastActiveTime;
  }

  @Override
  @DynamoDbPartitionKey
  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channel) {
    this.channelName = channel;
  }

  @DynamoDbIgnoreNulls
  public String getTgUri() {
    return tgUri;
  }

  public void setTgUri(String tgUri) {
    this.tgUri = tgUri;
  }

  @DynamoDbIgnoreNulls
  public Instant getTgLastMessageTime() {
    return tgLastMessageTime;
  }

  public void setTgLastMessageTime(Instant tgLastMessageTime) {
    this.tgLastMessageTime = tgLastMessageTime;
  }

  @DynamoDbIgnoreNulls
  public String getTgLastMessageId() {
    return tgLastMessageId;
  }

  public void setTgLastMessageId(String tgLastMessageId) {
    this.tgLastMessageId = tgLastMessageId;
  }

  @DynamoDbIgnoreNulls
  public String getWsUri() {
    return wsUri;
  }

  public void setWsUri(String wsUri) {
    this.wsUri = wsUri;
  }

  @DynamoDbIgnoreNulls
  public Instant getWsLastMessageTime() {
    return wsLastMessageTime;
  }

  public void setWsLastMessageTime(Instant wsLastMessageTime) {
    this.wsLastMessageTime = wsLastMessageTime;
  }

  @DynamoDbIgnoreNulls
  public String getWsLastMessageId() {
    return wsLastMessageId;
  }

  public void setWsLastMessageId(String wsLastMessageId) {
    this.wsLastMessageId = wsLastMessageId;
  }

  @DynamoDbIgnoreNulls
  public String getAiUri() {
    return aiUri;
  }

  public void setAiUri(String aiUri) {
    this.aiUri = aiUri;
  }

  @DynamoDbIgnoreNulls
  public Instant getAiLastMessageTime() {
    return aiLastMessageTime;
  }

  public void setAiLastMessageTime(Instant aiLastMessageTime) {
    this.aiLastMessageTime = aiLastMessageTime;
  }

  @DynamoDbIgnoreNulls
  public String getAiLastMessageId() {
    return aiLastMessageId;
  }

  public void setAiLastMessageId(String aiLastMessageId) {
    this.aiLastMessageId = aiLastMessageId;
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

  @DynamoDbIgnoreNulls
  public Map<String, String> getPinnedMessages() {
    return pinnedMessages;
  }

  public void setPinnedMessages(Map<String, String> pinnedMessages) {
    this.pinnedMessages = pinnedMessages;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DynamoDbMember member = (DynamoDbMember) o;

    if (host != member.host) return false;
    if (!Objects.equals(id, member.id)) return false;
    if (!Objects.equals(channelName, member.channelName)) return false;
    if (!Objects.equals(tgUri, member.tgUri)) return false;
    if (!Objects.equals(tgLastActiveTime, member.tgLastActiveTime)) return false;
    if (!Objects.equals(tgLastMessageTime, member.tgLastMessageTime)) return false;
    if (!Objects.equals(tgLastMessageId, member.tgLastMessageId)) return false;
    if (!Objects.equals(wsUri, member.wsUri)) return false;
    if (!Objects.equals(wsLastActiveTime, member.wsLastActiveTime)) return false;
    if (!Objects.equals(wsLastMessageTime, member.wsLastMessageTime)) return false;
    if (!Objects.equals(wsLastMessageId, member.wsLastMessageId)) return false;
    if (!Objects.equals(aiUri, member.aiUri)) return false;
    if (!Objects.equals(aiLastActiveTime, member.aiLastActiveTime)) return false;
    if (!Objects.equals(aiLastMessageTime, member.aiLastMessageTime)) return false;
    if (!Objects.equals(aiLastMessageId, member.aiLastMessageId)) return false;
    if (!Objects.equals(userName, member.userName)) return false;
    if (!Objects.equals(peerMemberId, member.peerMemberId)) return false;
    return Objects.equals(pinnedMessages, member.pinnedMessages);
  }

  @Override
  public int hashCode() {
    int result = id != null ? id.hashCode() : 0;
    result = 31 * result + (channelName != null ? channelName.hashCode() : 0);
    result = 31 * result + (tgUri != null ? tgUri.hashCode() : 0);
    result = 31 * result + (tgLastActiveTime != null ? tgLastActiveTime.hashCode() : 0);
    result = 31 * result + (tgLastMessageTime != null ? tgLastMessageTime.hashCode() : 0);
    result = 31 * result + (tgLastMessageId != null ? tgLastMessageId.hashCode() : 0);
    result = 31 * result + (wsUri != null ? wsUri.hashCode() : 0);
    result = 31 * result + (wsLastActiveTime != null ? wsLastActiveTime.hashCode() : 0);
    result = 31 * result + (wsLastMessageTime != null ? wsLastMessageTime.hashCode() : 0);
    result = 31 * result + (wsLastMessageId != null ? wsLastMessageId.hashCode() : 0);
    result = 31 * result + (aiUri != null ? aiUri.hashCode() : 0);
    result = 31 * result + (aiLastActiveTime != null ? aiLastActiveTime.hashCode() : 0);
    result = 31 * result + (aiLastMessageTime != null ? aiLastMessageTime.hashCode() : 0);
    result = 31 * result + (aiLastMessageId != null ? aiLastMessageId.hashCode() : 0);
    result = 31 * result + (userName != null ? userName.hashCode() : 0);
    result = 31 * result + (host ? 1 : 0);
    result = 31 * result + (peerMemberId != null ? peerMemberId.hashCode() : 0);
    result = 31 * result + (pinnedMessages != null ? pinnedMessages.hashCode() : 0);
    return result;
  }

  public DynamoDbMember copy() {
    return new DynamoDbMember.DynamoDbMemberBuilder()
        .withId(this.id)
        .withChannelName(this.channelName)
        .withUserName(this.userName)
        .withHost(this.host)
        .withPeerMemberId(this.peerMemberId)
        .withTgUri(this.tgUri)
        .withTgLastActiveTime(this.tgLastActiveTime)
        .withTgLastMessageTime(this.tgLastMessageTime)
        .withTgLastMessageId(this.tgLastMessageId)
        .withWsUri(this.wsUri)
        .withWsLastActiveTime(this.wsLastActiveTime)
        .withWsLastMessageTime(this.wsLastMessageTime)
        .withWsLastMessageId(this.wsLastMessageId)
        .withAiUri(this.aiUri)
        .withAiLastActiveTime(this.aiLastActiveTime)
        .withAiLastMessageTime(this.aiLastMessageTime)
        .withAiLastMessageId(this.aiLastMessageId)
        .withPinnedMessageId(new HashMap<>(this.pinnedMessages))
        .build();
  }

  @Override
  public String toString() {
    return "DynamoDbMember{"
        + "id='"
        + id
        + '\''
        + ", channelName='"
        + channelName
        + '\''
        + ", tgUri='"
        + tgUri
        + '\''
        + ", tgLastActiveTime="
        + tgLastActiveTime
        + ", tgLastMessageTime="
        + tgLastMessageTime
        + ", tgLastMessageId='"
        + tgLastMessageId
        + '\''
        + ", wsUri='"
        + wsUri
        + '\''
        + ", wsLastActiveTime="
        + wsLastActiveTime
        + ", wsLastMessageTime="
        + wsLastMessageTime
        + ", wsLastMessageId='"
        + wsLastMessageId
        + '\''
        + ", aiUri='"
        + aiUri
        + '\''
        + ", aiLastActiveTime="
        + aiLastActiveTime
        + ", aiLastMessageTime="
        + aiLastMessageTime
        + ", aiLastMessageId='"
        + aiLastMessageId
        + '\''
        + ", userName='"
        + userName
        + '\''
        + ", host="
        + host
        + ", peerMemberId='"
        + peerMemberId
        + '\''
        + ", pinnedMessages="
        + pinnedMessages
        + '}';
  }

  public static class DynamoDbMemberBuilder {
    private final DynamoDbMember member;

    public DynamoDbMemberBuilder() {
      this.member = new DynamoDbMember();
    }

    public DynamoDbMemberBuilder withId(String id) {
      this.member.setId(id);
      return this;
    }

    public DynamoDbMemberBuilder withChannelName(String channelName) {
      this.member.setChannelName(channelName);
      return this;
    }

    public DynamoDbMemberBuilder withTgUri(String tgUri) {
      this.member.setTgUri(tgUri);
      return this;
    }

    public DynamoDbMemberBuilder withTgLastActiveTime(Instant tgLastActiveTime) {
      this.member.setTgLastActiveTime(tgLastActiveTime);
      return this;
    }

    public DynamoDbMemberBuilder withTgLastMessageId(String tgLastMessageId) {
      this.member.setTgLastMessageId(tgLastMessageId);
      return this;
    }

    public DynamoDbMemberBuilder withTgLastMessageTime(Instant tgLastMessageTime) {
      this.member.setTgLastMessageTime(tgLastMessageTime);
      return this;
    }

    public DynamoDbMemberBuilder withWsUri(String wsUri) {
      this.member.setWsUri(wsUri);
      return this;
    }

    public DynamoDbMemberBuilder withWsLastActiveTime(Instant wsLastActiveTime) {
      this.member.setWsLastActiveTime(wsLastActiveTime);
      return this;
    }

    public DynamoDbMemberBuilder withWsLastMessageTime(Instant wsLastMessageTime) {
      this.member.setWsLastMessageTime(wsLastMessageTime);
      return this;
    }

    public DynamoDbMemberBuilder withWsLastMessageId(String wsLastMessageId) {
      this.member.setWsLastMessageId(wsLastMessageId);
      return this;
    }

    public DynamoDbMemberBuilder withAiUri(String aiUri) {
      this.member.setAiUri(aiUri);
      return this;
    }

    public DynamoDbMemberBuilder withAiLastActiveTime(Instant aiLastActiveTime) {
      this.member.setAiLastActiveTime(aiLastActiveTime);
      return this;
    }

    public DynamoDbMemberBuilder withAiLastMessageTime(Instant aiLastMessageTime) {
      this.member.setAiLastMessageTime(aiLastMessageTime);
      return this;
    }

    public DynamoDbMemberBuilder withAiLastMessageId(String aiLastMessageId) {
      this.member.setAiLastMessageId(aiLastMessageId);
      return this;
    }

    public DynamoDbMemberBuilder withUserName(String userName) {
      this.member.setUserName(userName);
      return this;
    }

    public DynamoDbMemberBuilder withHost(boolean host) {
      this.member.setHost(host);
      return this;
    }

    public DynamoDbMemberBuilder withPeerMemberId(String peerMemberId) {
      this.member.setPeerMemberId(peerMemberId);
      return this;
    }

    public DynamoDbMemberBuilder withPinnedMessageId(Map<String, String> pinnedMessageId) {
      this.member.setPinnedMessages(pinnedMessageId);
      return this;
    }

    public DynamoDbMember build() {
      return this.member;
    }
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnoreNulls;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import ua.com.pragmasoft.k1te.backend.router.domain.Connector;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.shared.RoutingException;
import ua.com.pragmasoft.k1te.backend.tg.TelegramConnector;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;

@DynamoDbBean
public class DynamoDbMember implements Member {

  private String id;
  private String channelName;
  private String tgUri;
  private Instant tgLastTime;
  private String tgLastMessageId;
  private String wsUri;
  private Instant wsLastTime;
  private String wsLastMessageId;
  private String aiUri;
  private Instant aiLastTime;
  private String aiLastMessageId;
  private String userName;
  private boolean host;
  private String peerMemberId;
  private Map<String, String> pinnedMessages = new HashMap<>();

  public DynamoDbMember(
      String id,
      String channelName,
      String tgUri,
      Instant tgLastTime,
      String tgLastMessageId,
      String wsUri,
      Instant wsLastTime,
      String wsLastMessageId,
      String aiUri,
      Instant aiLastTime,
      String aiLastMessageId,
      String userName,
      boolean host,
      String peerMemberId,
      Map<String, String> pinnedMessageId) {
    this.id = id;
    this.channelName = channelName;
    this.tgUri = tgUri;
    this.tgLastTime = tgLastTime;
    this.tgLastMessageId = tgLastMessageId;
    this.wsUri = wsUri;
    this.wsLastTime = wsLastTime;
    this.wsLastMessageId = wsLastMessageId;
    this.aiUri = aiUri;
    this.aiLastTime = aiLastTime;
    this.aiLastMessageId = aiLastMessageId;
    this.userName = userName;
    this.host = host;
    this.peerMemberId = peerMemberId;
    this.pinnedMessages = pinnedMessageId;
  }

  public DynamoDbMember() {
    super();
  }

  @Override
  public String getConnectionUri() {
    String maybeTgUri = this.tgUri != null ? TelegramConnector.TG + ":" + this.tgUri : null;
    String maybeWsUri = this.wsUri != null ? WsConnector.WS + ":" + this.wsUri : null;
    String maybeAiUri = this.aiUri != null ? "ai:" + this.aiUri : null;

    String[] uris = {maybeTgUri, maybeWsUri, maybeAiUri};
    Instant[] lastTimes = {this.tgLastTime, this.wsLastTime, this.aiLastTime};

    String connectionUri = null;
    Instant mostRecentTime = null;

    for (int i = 0; i < uris.length; i++) {
      if (uris[i] != null && (mostRecentTime == null || lastTimes[i].isAfter(mostRecentTime))) {
        connectionUri = uris[i];
        mostRecentTime = lastTimes[i];
      }
    }

    if (connectionUri == null) throw new RoutingException("missing connectionUri");

    return connectionUri;
  }

  @Override
  public String getLastMessageId() {
    String connectionUri = this.getConnectionUri();
    String connectorId = Connector.connectorId(connectionUri);
    return switch (connectorId) {
      case (TelegramConnector.TG) -> getTgLastMessageId();
      case (WsConnector.WS) -> getWsLastMessageId();
      case ("ai") -> getAiLastMessageId();
      default -> throw new RoutingException("missing connectionUri");
    };
  }

  public void updateConnectionUri(String connectorId, String connectionUri) {
    updateConnectionUri(connectorId, connectionUri, null, null);
  }

  public void deleteConnection(String connectorId) {
    switch (connectorId) {
      case (TelegramConnector.TG) -> {
        this.setTgUri(null);
        this.setTgLastTime(null);
        this.setTgLastMessageId(null);
      }
      case (WsConnector.WS) -> {
        this.setWsUri(null);
        this.setWsLastTime(null);
        this.setWsLastMessageId(null);
      }
      case ("ai") -> {
        this.setAiUri(null);
        this.setAiLastTime(null);
        this.setAiLastMessageId(null);
      }
      default -> throw new IllegalStateException("Unsupported connector id");
    }
  }

  public boolean hasConnection(String connection) {
    return (this.tgUri != null && this.tgUri.equals(connection))
        || (this.wsUri != null && this.wsUri.equals(connection))
        || (this.aiUri != null && this.aiUri.equals(connection));
  }

  public void updateConnectionUri(
      String connectorId, String connectionUri, String messageId, Instant usageTime) {
    Instant time = usageTime != null ? usageTime : Instant.now();
    switch (connectorId) {
      case (TelegramConnector.TG) -> {
        this.setTgUri(connectionUri);
        this.setTgLastTime(time);
        this.setTgLastMessageId(messageId);
      }
      case (WsConnector.WS) -> {
        this.setWsUri(connectionUri);
        this.setWsLastTime(time);
        this.setWsLastMessageId(messageId);
      }
      case ("ai") -> {
        this.setAiUri(connectionUri);
        this.setAiLastTime(time);
        this.setAiLastMessageId(messageId);
      }
      default -> throw new IllegalStateException("Unsupported connector id");
    }
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

  @DynamoDbIgnoreNulls
  public String getTgUri() {
    return tgUri;
  }

  public void setTgUri(String tgUri) {
    this.tgUri = tgUri;
  }

  @DynamoDbIgnoreNulls
  public Instant getTgLastTime() {
    return tgLastTime;
  }

  public void setTgLastTime(Instant tgLastTime) {
    this.tgLastTime = tgLastTime;
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
  public Instant getWsLastTime() {
    return wsLastTime;
  }

  public void setWsLastTime(Instant wsLastTime) {
    this.wsLastTime = wsLastTime;
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
  public Instant getAiLastTime() {
    return aiLastTime;
  }

  public void setAiLastTime(Instant aiLastTime) {
    this.aiLastTime = aiLastTime;
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

  public void addPinnedMessage(String memberId, String pinnedMessageId) {
    this.pinnedMessages.put(memberId, pinnedMessageId);
  }

  public void deletePinnedMessage(String memberId) {
    this.pinnedMessages.remove(memberId);
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
        + ", tgLastTime='"
        + tgLastTime
        + '\''
        + ", tgLastMessageId='"
        + tgLastMessageId
        + '\''
        + ", wsUri='"
        + wsUri
        + '\''
        + ", wsLastTime='"
        + wsLastTime
        + '\''
        + ", wsLastMessageId='"
        + wsLastMessageId
        + '\''
        + ", aiUri='"
        + aiUri
        + '\''
        + ", aiLastTime='"
        + aiLastTime
        + '\''
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
        + ", pinnedMessageId="
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

    public DynamoDbMemberBuilder withTgLastTime(Instant tgLastTime) {
      this.member.setTgLastTime(tgLastTime);
      return this;
    }

    public DynamoDbMemberBuilder withTgLastMessageId(String tgLastMessageId) {
      this.member.setTgLastMessageId(tgLastMessageId);
      return this;
    }

    public DynamoDbMemberBuilder withWsUri(String wsUri) {
      this.member.setWsUri(wsUri);
      return this;
    }

    public DynamoDbMemberBuilder withWsLastTime(Instant wsLastTime) {
      this.member.setWsLastTime(wsLastTime);
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

    public DynamoDbMemberBuilder withAiLastTime(Instant aiLastTime) {
      this.member.setAiLastTime(aiLastTime);
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

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.time.Instant;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnoreNulls;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
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
  private Integer pinnedMessageId;

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
      Integer pinnedMessageId) {
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
    this.pinnedMessageId = pinnedMessageId;
  }

  public DynamoDbMember() {
    super();
  }

  public void resolveConnectionUri(String connectorId, String connectionUri) {
    updateConnectionUri(connectorId, connectionUri, null, null);
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

  @Override
  @DynamoDbIgnoreNulls
  public String getTgUri() {
    return tgUri;
  }

  public void setTgUri(String tgUri) {
    this.tgUri = tgUri;
  }

  @Override
  @DynamoDbIgnoreNulls
  public Instant getTgLastTime() {
    return tgLastTime;
  }

  public void setTgLastTime(Instant tgLastTime) {
    this.tgLastTime = tgLastTime;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getTgLastMessageId() {
    return tgLastMessageId;
  }

  public void setTgLastMessageId(String tgLastMessageId) {
    this.tgLastMessageId = tgLastMessageId;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getWsUri() {
    return wsUri;
  }

  public void setWsUri(String wsUri) {
    this.wsUri = wsUri;
  }

  @Override
  @DynamoDbIgnoreNulls
  public Instant getWsLastTime() {
    return wsLastTime;
  }

  public void setWsLastTime(Instant wsLastTime) {
    this.wsLastTime = wsLastTime;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getWsLastMessageId() {
    return wsLastMessageId;
  }

  public void setWsLastMessageId(String wsLastMessageId) {
    this.wsLastMessageId = wsLastMessageId;
  }

  @Override
  @DynamoDbIgnoreNulls
  public String getAiUri() {
    return aiUri;
  }

  public void setAiUri(String aiUri) {
    this.aiUri = aiUri;
  }

  @Override
  @DynamoDbIgnoreNulls
  public Instant getAiLastTime() {
    return aiLastTime;
  }

  public void setAiLastTime(Instant aiLastTime) {
    this.aiLastTime = aiLastTime;
  }

  @Override
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

  @Override
  @DynamoDbIgnoreNulls
  public Integer getPinnedMessageId() {
    return pinnedMessageId;
  }

  public void setPinnedMessageId(Integer pinnedMessageId) {
    this.pinnedMessageId = pinnedMessageId;
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
        + pinnedMessageId
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

    public DynamoDbMemberBuilder withPinnedMessageId(Integer pinnedMessageId) {
      this.member.setPinnedMessageId(pinnedMessageId);
      return this;
    }

    public DynamoDbMember build() {
      return this.member;
    }
  }
}

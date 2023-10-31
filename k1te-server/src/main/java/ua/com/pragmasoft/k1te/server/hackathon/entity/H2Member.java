package ua.com.pragmasoft.k1te.server.hackathon.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;

@Entity
@Table(name = "Member")
public class H2Member extends PanacheEntityBase implements Member {

  @Id
  private String id;

  @Column(nullable = false)
  private String userName;

  @Column(nullable = false)
  private boolean host;

  @Column(nullable = false, unique = true)
  private String connectionUri;

  private String peerMemberId;

  @Column(name = "channelName", updatable = false, insertable = false)
  private String channelName;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channelName", nullable = false)
  private H2Channel channel;

  @Override
  public String getId() {
    return id;
  }

  @Override
  public String getChannelName() {
    return this.channelName;
  }

  @Override
  public String getUserName() {
    return userName;
  }

  @Override
  public boolean isHost() {
    return host;
  }

  @Override
  public String getConnectionUri() {
    return connectionUri;
  }

  @Override
  public String getPeerMemberId() {
    return peerMemberId;
  }

  public H2Channel getChannel() {
    return channel;
  }

  public void setId(String id) {
    this.id = id;
  }

  public void setUserName(String userName) {
    this.userName = userName;
  }

  public void setHost(boolean host) {
    this.host = host;
  }

  public void setConnectionUri(String connectionUri) {
    this.connectionUri = connectionUri;
  }

  public void setPeerMemberId(String peerMemberId) {
    this.peerMemberId = peerMemberId;
  }

  public void setChannel(H2Channel channel) {
    this.channelName = channel.getChannelName();
    this.channel = channel;
  }

}

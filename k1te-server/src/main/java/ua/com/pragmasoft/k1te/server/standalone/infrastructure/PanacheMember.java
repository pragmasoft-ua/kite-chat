/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.infrastructure;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;

@Entity
@Table(name = "Member")
@IfBuildProfile("standalone")
public class PanacheMember extends PanacheEntityBase implements Member {

  @EmbeddedId private MemberPK memberPK;

  @Column(nullable = false)
  private String userName;

  @Column(nullable = false)
  private boolean host;

  @Column(nullable = false, unique = true)
  private String connectionUri;

  private String peerMemberId;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "channelName", nullable = false, insertable = false, updatable = false)
  private PanacheChannel channel;

  @OneToMany(mappedBy = "member", fetch = FetchType.LAZY)
  private List<PanachePinnedMessage> pinnedMessages;

  public static PanacheMember of(
      String memberId,
      String channelName,
      String userName,
      boolean host,
      String connectionUri,
      String peerMemberId) {
    return new PanacheMember(
        buildId(channelName, memberId), userName, host, connectionUri, peerMemberId);
  }

  public static MemberPK buildId(String channelName, String memberId) {
    return new MemberPK(channelName, memberId);
  }

  public static PanacheMember findByMemberId(String memberId) {
    return find("id.memberId", memberId).firstResult();
  }

  public PanacheMember() {}

  public PanacheMember(
      MemberPK memberPK, String userName, boolean host, String connectionUri, String peerMemberId) {
    this.memberPK = memberPK;
    this.userName = userName;
    this.host = host;
    this.connectionUri = connectionUri;
    this.peerMemberId = peerMemberId;
  }

  @Override
  public void updateUnAnsweredMessage(Member toMember, String messageId) {
    // TODO: 24.11.2023
  }

  @Override
  public void updatePeer(String peerMemberId) {}

  @Override
  public void updateConnection(String connectionUri, String messageId, Instant usageTime) {}

  @Override
  public void deleteUnAnsweredMessage(Member toMember) {}

  @Override
  public String getId() {
    return this.memberPK.memberId;
  }

  @Override
  public String getChannelName() {
    return this.memberPK.channelName;
  }

  @Override
  public String getUserName() {
    return this.userName;
  }

  @Override
  public boolean isHost() {
    return this.host;
  }

  public String getConnectionUri() {
    return this.connectionUri;
  }

  @Override
  public String getPeerMemberId() {
    return this.peerMemberId;
  }

  public MemberPK getMemberPK() {
    return memberPK;
  }

  public void setMemberPK(MemberPK memberPK) {
    this.memberPK = memberPK;
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

  public void setChannel(PanacheChannel channel) {
    this.channel = channel;
  }

  public PanacheChannel getChannel() {
    return channel;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    PanacheMember member = (PanacheMember) o;

    return Objects.equals(memberPK, member.memberPK);
  }

  @Override
  public int hashCode() {
    return memberPK != null ? memberPK.hashCode() : 0;
  }

  @Embeddable
  public static class MemberPK implements Serializable {

    private String channelName;
    private String memberId;

    public MemberPK() {}

    public MemberPK(String channelName, String memberId) {
      this.channelName = channelName;
      this.memberId = memberId;
    }

    public String getMemberId() {
      return memberId;
    }

    public void setMemberId(String memberId) {
      this.memberId = memberId;
    }

    public String getChannelName() {
      return channelName;
    }

    public void setChannelName(String channelName) {
      this.channelName = channelName;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      MemberPK memberPK = (MemberPK) o;

      if (!Objects.equals(channelName, memberPK.channelName)) return false;
      return Objects.equals(memberId, memberPK.memberId);
    }

    @Override
    public int hashCode() {
      int result = channelName != null ? channelName.hashCode() : 0;
      result = 31 * result + (memberId != null ? memberId.hashCode() : 0);
      return result;
    }
  }
}

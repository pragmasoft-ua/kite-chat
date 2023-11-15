/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.infrastructure;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Objects;

@Entity
@Table(name = "PinnedMessage")
public class PanachePinnedMessage extends PanacheEntityBase {

  @EmbeddedId private PinnedMessagePK messagePK;

  private Integer messageId;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumns(
      value = {
        @JoinColumn(
            name = "channelName",
            referencedColumnName = "channelName",
            insertable = false,
            updatable = false),
        @JoinColumn(
            name = "memberId",
            referencedColumnName = "memberId",
            insertable = false,
            updatable = false)
      },
      foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
  private PanacheMember member;

  public PanachePinnedMessage(PinnedMessagePK messagePK, Integer messageId) {
    this.messagePK = messagePK;
    this.messageId = messageId;
  }

  public PanachePinnedMessage() {}

  public PinnedMessagePK getMessagePK() {
    return messagePK;
  }

  public void setMessagePK(PinnedMessagePK messagePK) {
    this.messagePK = messagePK;
  }

  public Integer getMessageId() {
    return messageId;
  }

  public void setMessageId(Integer messageId) {
    this.messageId = messageId;
  }

  @Embeddable
  public static class PinnedMessagePK implements Serializable {
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumns(
        value = {
          @JoinColumn(name = "channelName", referencedColumnName = "channelName"),
          @JoinColumn(name = "memberId", referencedColumnName = "memberId")
        },
        foreignKey = @ForeignKey(value = ConstraintMode.NO_CONSTRAINT))
    private PanacheMember member;

    private String toMemberId;

    public PinnedMessagePK(PanacheMember member, String toMemberId) {
      this.member = member;
      this.toMemberId = toMemberId;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      PinnedMessagePK that = (PinnedMessagePK) o;

      if (!Objects.equals(member, that.member)) return false;
      return Objects.equals(toMemberId, that.toMemberId);
    }

    @Override
    public int hashCode() {
      int result = member != null ? member.hashCode() : 0;
      result = 31 * result + (toMemberId != null ? toMemberId.hashCode() : 0);
      return result;
    }

    public PanacheMember getMember() {
      return member;
    }

    public void setMember(PanacheMember member) {
      this.member = member;
    }

    public PinnedMessagePK() {}

    public String getToMemberId() {
      return toMemberId;
    }

    public void setToMemberId(String memberId) {
      this.toMemberId = memberId;
    }
  }
}

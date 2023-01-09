package ua.com.pragmasoft.k1te.router.domain.payload;

import java.util.Objects;

import ua.com.pragmasoft.k1te.router.domain.Id;

public final class JoinChannel implements CommandPayload {

  private static final long serialVersionUID = 1L;
  public String memberId;
  public String memberName;
  public String channelName;

  public JoinChannel(String memberId, String memberName, String channelName) {
    Objects.requireNonNull(memberId, "memberId");
    this.memberId = memberId;
    this.memberName = memberName;
    this.channelName = Id.validate(channelName);
  }

  public JoinChannel(String memberId, String memberName) {
    this.memberId = Id.validate(memberId);
    this.memberName = memberName;
  }

  public JoinChannel(String memberId) {
    this(memberId, "User_" + memberId);
  }

  @Override
  public Type type() {
    return Type.JOIN;
  }

  @Override
  public String toString() {
    return "JoinChannel [memberId=" + memberId + ", memberName=" + memberName + ", channelName="
        + channelName + "]";
  }

}

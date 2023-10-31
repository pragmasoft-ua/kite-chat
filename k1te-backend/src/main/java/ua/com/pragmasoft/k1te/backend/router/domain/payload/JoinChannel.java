/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.util.Objects;

public record JoinChannel(String memberId, String memberName, String channelName)
    implements Payload {

  public JoinChannel(String memberId, String memberName, String channelName) {
    Objects.requireNonNull(memberId, "memberId");
    Objects.requireNonNull(channelName, "channelName");
    this.memberId = memberId;
    this.memberName = null != memberName ? memberName : "User_" + memberId;
    this.channelName = channelName;
  }

  @Override
  public Type type() {
    return Type.JOIN;
  }

  @Override
  public String toString() {
    return type().label
        + " [memberId="
        + memberId
        + ", memberName="
        + memberName
        + ", channelName="
        + channelName
        + "]";
  }
}

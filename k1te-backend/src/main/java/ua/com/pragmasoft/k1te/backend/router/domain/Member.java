/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;

public interface Member {

  public String getId();

  public String getChannelName();

  public String getUserName();

  public boolean isHost();

  public String getConnectionUri();

  public String getPeerMemberId();

  void updateUnAnsweredMessage(Member toMember, String messageId);

  void updatePeer(String peerMemberId);

  void updateConnection(String connectionUri, String messageId, Instant usageTime);

  void deleteUnAnsweredMessage(Member toMember);
}

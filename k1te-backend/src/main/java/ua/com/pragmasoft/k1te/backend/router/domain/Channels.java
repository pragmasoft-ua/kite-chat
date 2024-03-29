/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;

public interface Channels {

  Member hostChannel(String channel, String memberId, String ownerConnection, String title);

  Member dropChannel(String ownerConnection);

  Member joinChannel(String channelName, String memberId, String connection, String memberName);

  Member leaveChannel(String connection);

  Member find(String memberConnection);

  Member find(String channel, String memberId);

  Member findHost(String channelName);

  String findUnAnsweredMessage(Member from, Member to);

  Member switchConnection(String channelName, String memberId, String newConnection);

  void updateUnAnsweredMessage(Member from, Member to, String pinnedMessagedId);

  void updatePeer(Member member, String peerMemberId);

  void updateConnection(
      Member memberToUpdate, String connectionUri, String messageId, Instant usageTime);

  void deleteUnAnsweredMessage(Member from, Member to);
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.domain;

import static ua.com.pragmasoft.k1te.server.standalone.infrastructure.PanacheMember.buildId;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.ChannelName;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.shared.ConflictException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;
import ua.com.pragmasoft.k1te.server.standalone.infrastructure.PanacheChannel;
import ua.com.pragmasoft.k1te.server.standalone.infrastructure.PanacheMember;

@IfBuildProfile("standalone")
@ApplicationScoped
@Transactional
public class PanacheChannels implements Channels {

  private static final Logger log = LoggerFactory.getLogger(PanacheChannels.class);

  @Override
  public Member hostChannel(
      String channelName, String memberId, String ownerConnection, String title) {

    ChannelName.validate(channelName);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(ownerConnection, "owner connection");

    if (null == title) {
      title = channelName;
    }

    if (PanacheChannel.findById(channelName) != null) {
      throw new ConflictException("Such channel name already exists");
    }
    if (PanacheMember.findByMemberId(memberId) != null) {
      throw new ConflictException(
          "You already have Channel - /leave or /drop it to host a new one");
    }

    PanacheChannel channel = new PanacheChannel();
    channel.setChannelName(channelName);
    channel.setHostId(memberId);

    PanacheMember member =
        PanacheMember.of(memberId, channelName, title, true, ownerConnection, null);

    channel.addMember(member);
    channel.persistAndFlush();
    log.debug("Channel {} was created by Host {}", channelName, memberId);

    return member;
  }

  @Override
  public Member dropChannel(String ownerConnection) {
    Objects.requireNonNull(ownerConnection, "owner connection");

    Member member = find(ownerConnection);

    if (!member.isHost()) throw new ValidationException("Only host member can drop its channel");

    String channelName = member.getChannelName();
    PanacheChannel.deleteById(channelName); // Delete with Cascade
    PanacheChannel.flush();
    log.debug("Channel {} was deleted", channelName);
    return member;
  }

  @Override
  public Member joinChannel(
      String channelName, String memberId, String connection, String userName) {
    ChannelName.validate(channelName);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(userName, "user name");

    PanacheChannel channel = PanacheChannel.findById(channelName);
    if (channel == null) throw new NotFoundException();

    PanacheMember existingMember = PanacheMember.findByMemberId(memberId);
    if (existingMember != null) {
      if (existingMember.getConnectionUri().equals(connection))
        throw new ConflictException("You can't have more than one open Channel");

      existingMember.setConnectionUri(connection);
      existingMember.persistAndFlush();
      return existingMember;
    }

    PanacheMember member =
        PanacheMember.of(memberId, channelName, userName, false, connection, channel.getHostId());

    member.persistAndFlush();
    log.debug("Member {} joined the Channel {}", memberId, channelName);
    return member;
  }

  @Override
  public Member leaveChannel(String connection) {
    Objects.requireNonNull(connection, "connection");

    Member member = find(connection);

    if (member.isHost())
      throw new ValidationException("Host can't leave the chat, you can only drop it");

    PanacheMember.deleteById(buildId(member.getId(), member.getChannelName()));
    PanacheMember.flush();
    log.debug("Member {} left the Channel", member.getId());
    return member;
  }

  @Override
  public Member find(String memberConnection) {
    Objects.requireNonNull(memberConnection, "connection");
    PanacheMember member = PanacheMember.find("connectionUri", memberConnection).firstResult();
    if (member == null) throw new NotFoundException();
    return member;
  }

  @Override
  public Member find(String channelName, String memberId) {
    PanacheMember member = PanacheMember.findById(buildId(memberId, channelName));

    if (member == null) throw new NotFoundException();

    return member;
  }

  @Override
  public void updatePeer(Member recipientMember, String peerMemberId) {
    Objects.requireNonNull(peerMemberId, "peer Member");
    if (peerMemberId.equals(recipientMember.getPeerMemberId())) {
      return;
    }
    PanacheMember member =
        PanacheMember.findById(buildId(recipientMember.getId(), recipientMember.getChannelName()));
    member.setPeerMemberId(peerMemberId);
    member.persistAndFlush();
  }
}

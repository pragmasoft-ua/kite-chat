package ua.com.pragmasoft.k1te.server.hackathon.service;

import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.ChannelName;
import ua.com.pragmasoft.k1te.backend.router.domain.Channels;
import ua.com.pragmasoft.k1te.backend.router.domain.Member;
import ua.com.pragmasoft.k1te.backend.shared.ConflictException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;
import ua.com.pragmasoft.k1te.server.hackathon.entity.H2Channel;
import ua.com.pragmasoft.k1te.server.hackathon.entity.H2Member;

import java.util.Objects;

@ApplicationScoped
@Transactional
public class ChannelsService implements Channels {

  private static final Logger log = LoggerFactory.getLogger(ChannelsService.class);

  @Override
  public Member hostChannel(String channelName, String memberId, String ownerConnection, String title) {

    ChannelName.validate(channelName);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(ownerConnection, "owner connection");

    if (null == title) {
      title = channelName;
    }

    if (H2Channel.findById(channelName) != null) {
      throw new ConflictException("Such channel name already exists");
    }
    if (H2Member.findById(memberId) != null) {
      throw new ConflictException("You already have Channel - /leave or /drop it to host a new one");
    }

    H2Channel channel = new H2Channel();
    channel.setChannelName(channelName);
    channel.setHostId(memberId);

    H2Member member = new H2Member();
    member.setId(memberId);
    member.setChannel(channel);
    member.setUserName(title);
    member.setHost(true);
    member.setConnectionUri(ownerConnection);

    channel.addMember(member);
    channel.persistAndFlush();
    log.debug("Channel {} was created by Host {}", channelName, memberId);

    return member;
  }

  @Override
  public Member dropChannel(String ownerConnection) {
    Objects.requireNonNull(ownerConnection, "owner connection");

    Member member = find(ownerConnection);

    if (!member.isHost())
      throw new ValidationException("Only host member can drop its channel");

    String channelName = member.getChannelName();
    H2Channel.deleteById(channelName); //Delete with Cascade
    H2Channel.flush();
    log.debug("Channel {} was deleted", channelName);
    return member;
  }

  @Override
  public Member joinChannel(String channelName, String memberId, String connection, String userName) {
    ChannelName.validate(channelName);
    Objects.requireNonNull(memberId, "member id");
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(userName, "user name");

    if (H2Member.findById(memberId) != null)
      throw new ConflictException("You can have only one open chat");

    H2Channel channel = H2Channel.findById(channelName);
    if (channel == null)
      throw new NotFoundException();

    H2Member member = new H2Member();
    member.setId(memberId);
    member.setUserName(userName);
    member.setChannel(channel);
    member.setConnectionUri(connection);
    member.setHost(false);
    member.setPeerMemberId(channel.getHostId());

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

    H2Member.deleteById(member.getId());
    H2Member.flush();
    log.debug("Member {} left the Channel", member.getId());
    return member;
  }

  @Override
  public Member find(String memberConnection) {
    Objects.requireNonNull(memberConnection, "connection");
    H2Member member = H2Member.find("connectionUri", memberConnection).firstResult();
    if (member == null)
      throw new NotFoundException();
    return member;
  }

  @Override
  public Member find(String channel, String memberId) {
    H2Member member = H2Member.find("id=:memberId AND channelName=:channelName", Parameters
        .with("channelName", channel)
        .and("memberId", memberId))
      .firstResult();

    if (member == null)
      throw new NotFoundException();

    return member;
  }

  @Override
  public void updatePeer(Member recipientMember, String peerMemberId) {
    Objects.requireNonNull(peerMemberId, "peer Member");
    if (peerMemberId.equals(recipientMember.getPeerMemberId())) {
      return;
    }
    H2Member member = H2Member.findById(recipientMember.getId());
    member.setPeerMemberId(peerMemberId);
    member.persistAndFlush();
  }
}

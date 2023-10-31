package ua.com.pragmasoft.k1te.server.hackathon.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;


@Entity
@Table(name = "Channel")
public class H2Channel extends PanacheEntityBase {

  @Id
  private String channelName;

  @Column(unique = true)
  private String hostId;

  @OneToMany(mappedBy = "channel", fetch = FetchType.LAZY, cascade = {CascadeType.REMOVE, CascadeType.PERSIST})
  private List<H2Member> members = new ArrayList<>();

  public void addMember(H2Member member) {
    this.members.add(member);
    member.setChannel(this);
  }

  public String getChannelName() {
    return channelName;
  }

  public String getHostId() {
    return hostId;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
  }

  public void setHostId(String hostId) {
    this.hostId = hostId;
  }
}

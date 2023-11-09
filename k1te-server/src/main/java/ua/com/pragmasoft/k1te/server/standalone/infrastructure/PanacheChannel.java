/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.infrastructure;

import io.quarkus.arc.profile.IfBuildProfile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Channel")
@IfBuildProfile("standalone")
public class PanacheChannel extends PanacheEntityBase {

  @Id private String channelName;

  @Column(unique = true)
  private String hostId;

  @OneToMany(
      mappedBy = "channel",
      fetch = FetchType.LAZY,
      cascade = {CascadeType.REMOVE, CascadeType.PERSIST})
  private List<PanacheMember> members = new ArrayList<>();

  public void addMember(PanacheMember member) {
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

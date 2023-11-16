/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

public interface Member {

  public String getId();

  public String getChannelName();

  public String getUserName();

  public boolean isHost();

  public String getConnectionUri();

  public String getPeerMemberId();
}

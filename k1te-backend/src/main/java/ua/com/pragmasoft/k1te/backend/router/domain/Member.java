/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;

public interface Member {

  public String getId();

  public String getChannelName();

  public String getUserName();

  public boolean isHost();

  public String getTgUri();

  public Instant getTgLastTime();

  public Integer getTgLastMessageId();

  public String getWsUri();

  public Instant getWsLastTime();

  public Integer getWsLastMessageId();

  public String getAiUri();

  public Instant getAiLastTime();

  public Integer getAiLastMessageId();

  public String getPeerMemberId();

  public Integer getPinnedMessageId();
}

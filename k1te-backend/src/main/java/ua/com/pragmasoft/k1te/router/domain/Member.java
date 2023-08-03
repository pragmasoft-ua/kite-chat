package ua.com.pragmasoft.k1te.router.domain;

public interface Member {

  public String getId();

  public String getChannelName();

  public String getUserName();

  public boolean isHost();

  public String getConnectionUri();

  public String getPeerMemberId();

}

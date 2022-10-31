package ua.com.pragmasoft.k1te.chat;

public record MessageId(String raw) {

  public static MessageId fromLong(long id) {
    return new MessageId(Long.toString(id));
  }

}

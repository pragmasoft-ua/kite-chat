package ua.com.pragmasoft.k1te.router;

public record MessageId(String raw) {

  public static MessageId fromLong(long id) {
    return new MessageId(Long.toString(id));
  }

}

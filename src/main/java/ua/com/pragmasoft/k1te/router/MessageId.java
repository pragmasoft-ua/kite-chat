package ua.com.pragmasoft.k1te.router;

import java.math.BigInteger;
import java.util.Base64;

public record MessageId(String raw) {

  public static MessageId MISSING = new MessageId(null);

  public static MessageId fromLong(long id) {
    final String idAsString =
        Base64.getEncoder().encodeToString(BigInteger.valueOf(id).toByteArray());
    return new MessageId(idAsString);
  }

}

package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URL;
import java.time.Instant;

public record UploadResponse(URL url, String messageId, Instant created)
    implements MessagePayload {

  public UploadResponse(URL url, String messageId) {
    this(url, messageId, Instant.now());
  }

  @Override
  public Type type() {
    return Type.UPL;
  }

  @Override
  public String toString() {
    return type().label
        + " [url=" + url
        + ", messageId=" + messageId
        + ", created=" + created
        + "]";
  }

}

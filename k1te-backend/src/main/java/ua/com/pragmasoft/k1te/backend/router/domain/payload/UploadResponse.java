package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URI;

public record UploadResponse(URI uri, String messageId)
    implements MessagePayload {

  public UploadResponse(String uri, String messageId) {
    this(URI.create(uri), messageId);
  }

  @Override
  public Type type() {
    return Type.UPL;
  }

  @Override
  public String toString() {
    return type().label
        + " [uri=" + uri
        + ", messageId=" + messageId
        + "]";
  }

}

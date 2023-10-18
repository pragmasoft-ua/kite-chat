package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URI;

public record UploadResponse(String messageId, URI canonicalUri, URI uploadUri)
    implements MessagePayload {

  public UploadResponse(String messageId, URI uri) {
    this(messageId, uri, null);
  }

  @Override
  public Type type() {
    return Type.UPL;
  }

  @Override
  public String toString() {
    return type().label
        + " [uri=" + canonicalUri
        + ", messageId=" + messageId
        + "]";
  }

}

package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.time.Instant;

public record UploadRequest(String fileName, String fileType, long fileSize, String messageId, Instant created)
    implements MessagePayload {

  public UploadRequest(String fileName, String fileType, long fileSize, String messageId) {
    this(fileName, fileType, fileSize, messageId, Instant.now());
  }

  @Override
  public Type type() {
    return Type.UPL;
  }

  @Override
  public String toString() {
    return type().label
        + " [fileName=" + fileName
        + ", fileType=" + fileType
        + ", fileSize=" + fileSize
        + ", messageId=" + messageId
        + ", created=" + created
        + "]";
  }

}

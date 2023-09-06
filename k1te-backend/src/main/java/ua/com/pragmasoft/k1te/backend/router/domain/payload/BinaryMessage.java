package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URL;
import java.time.Instant;

// type: MsgType.BIN;
// messageId: string;
// timestamp: Date;
// url: string;
// fileName?: string;
// fileType?: string;
// fileSize?: number;

public record BinaryMessage(URL url, String fileName, String fileType, long fileSize, String messageId, Instant created)
    implements MessagePayload {

  public BinaryMessage(URL url, String fileName, String fileType, long fileSize, String messageId) {
    this(url, fileName, fileType, fileSize, messageId, Instant.now());
  }

  public BinaryMessage(URL url, String fileName, String fileType, long fileSize) {
    this(url, fileName, fileType, fileSize, "-");
  }

  @Override
  public Type type() {
    return Type.BIN;
  }

  @Override
  public String toString() {
    return type().label
        + " [url=" + url
        + ", fileName=" + fileName
        + ", fileType=" + fileType
        + ", fileSize=" + fileSize
        + ", messageId=" + messageId
        + ", created=" + created
        + "]";
  }

}

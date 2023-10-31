/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URI;
import java.time.Instant;

public record BinaryMessage(
    URI uri, String fileName, String fileType, long fileSize, String messageId, Instant created)
    implements BinaryPayload {

  public BinaryMessage(
      String uri,
      String fileName,
      String fileType,
      long fileSize,
      String messageId,
      Instant created) {
    this(URI.create(uri), fileName, fileType, fileSize, messageId, created);
  }

  public BinaryMessage(
      String url, String fileName, String fileType, long fileSize, String messageId) {
    this(url, fileName, fileType, fileSize, messageId, Instant.now());
  }

  public BinaryMessage(String url, String fileName, String fileType, long fileSize) {
    this(url, fileName, fileType, fileSize, "-");
  }

  @Override
  public String toString() {
    return type().label
        + " [uri="
        + uri
        + ", fileName="
        + fileName
        + ", fileType="
        + fileType
        + ", fileSize="
        + fileSize
        + ", messageId="
        + messageId
        + ", created="
        + created
        + "]";
  }
}

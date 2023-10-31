/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain.payload;

import java.net.URI;
import java.time.Instant;

public non-sealed interface BinaryPayload extends MessagePayload {

  URI uri();

  String fileName();

  String fileType();

  long fileSize();

  Instant created();

  default boolean isImage() {
    return fileType().startsWith("image");
  }

  default Type type() {
    return Type.BIN;
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.net.URI;
import java.time.Instant;

@RecordBuilder
public record SendBinaryRecord(
    URI uri,
    String fileName,
    String fileType,
    long fileSize,
    String text,
    Mode mode,
    String messageId,
    Instant timestamp)
    implements SendBinary, SendBinaryRecordBuilder.With {

  @Override
  public SendText overrideText(String text) {
    return withText(text);
  }
}

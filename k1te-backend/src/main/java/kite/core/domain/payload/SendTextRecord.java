/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

@RecordBuilder
public record SendTextRecord(String text, String messageId, Mode mode, Instant timestamp)
    implements SendText, SendTextRecordBuilder.With {

  @Override
  public SendText overrideText(String text) {
    return withText(text);
  }
}

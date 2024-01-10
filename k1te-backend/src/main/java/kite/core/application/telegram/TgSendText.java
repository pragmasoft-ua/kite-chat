/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import kite.core.domain.payload.SendText;

@RecordBuilder
public record TgSendText(
    String text,
    String messageId,
    Long origChatId,
    Integer origMessageId,
    Mode mode,
    Instant timestamp)
    implements SendText, TgSendTextBuilder.With {

  @Override
  public SendText overrideText(String text) {
    return withText(text);
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.model.File;
import com.pengrad.telegrambot.request.GetFile;
import com.pengrad.telegrambot.response.GetFileResponse;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.net.URI;
import java.time.Instant;
import kite.core.domain.payload.SendBinary;
import kite.core.domain.payload.SendText;

/**
 * More efficient implementation of a BinaryPayload lazily creates file url which is only needed
 * when routed to other connectors. Exposes fileId which is needed to re-route the file inside the
 * Telegram connector
 */
@RecordBuilder
public record TgSendBinary(
    TelegramBot bot,
    String fileId,
    String fileName,
    String fileType,
    long fileSize,
    String text,
    String messageId,
    Long origChatId,
    Integer origMessageId,
    Mode mode,
    Instant timestamp)
    implements SendBinary, TgSendBinaryBuilder.With {
  /** Lazily retrieves URI. */
  @Override
  public URI uri() {
    GetFile getFile = new GetFile(this.fileId());
    GetFileResponse getFileResponse = this.bot.execute(getFile);
    File file = getFileResponse.file();
    String uriString = this.bot.getFullFilePath(file);
    return URI.create(uriString);
  }

  @Override
  public SendText overrideText(String text) {
    return withText(text);
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.application.telegram;

import static kite.core.domain.payload.SendText.Mode.EDITED;
import static kite.core.domain.payload.SendText.Mode.NEW;

import java.net.URI;
import java.time.Instant;
import kite.core.domain.Route;
import kite.core.domain.payload.*;
import kite.core.domain.payload.Error;

public class TelegramBaseTest {
  protected static final Long ORIG_CHAT_ID = 583362898L;
  protected static final Integer ORIGIN_MESSAGE_ID = 763;

  protected static final String FROM_ID = "9nbhoy";
  protected static final Route CHAT_ID = Route.of("tg:" + FROM_ID);
  protected static final String MESSAGE_ID = "l7";
  protected static final int TIME = 1705567177;
  protected static final Instant TIMESTAMP = Instant.ofEpochSecond(1705567177);
  protected static final String TEXT = "Some text here!";

  protected static final String DOCUMENT_NAME = "function.zip";
  protected static final String DOCUMENT_TYPE = "application/zip";
  protected static final long DOCUMENT_SIZE = 29724836L;
  protected static final String DOCUMENT_ID =
      "BQACAgIAAxkBAAIC-2Wn1_N_Tsdi4fK6s8mh5rWWlZPeAAKLPwACww45SaNcsvD0LJCdNAQ";

  protected static final String IMAGE_NAME = "Алексей-2024-01-18T08:39:37Z.jpg";
  protected static final String IMAGE_TYPE = "image/jpeg";
  protected static final long IMAGE_SIZE = 86299;

  protected static final URI BASE_URI = URI.create("https://kite.chat.com");

  protected static final TgSendBinary TG_SEND_BINARY = tgSendBinary();
  protected static final TgSendText TG_SEND_TEXT = tgSendText();
  protected static final SendTextRecord NEW_SEND_TEXT = sendTextRecord(NEW);
  protected static final SendBinaryRecord NEW_SEND_DOCUMENT = sendDocumentRecord(NEW);
  protected static final SendBinaryRecord NEW_SEND_PHOTO = sendPhotoRecord(NEW);
  protected static final SendTextRecord EDIT_SEND_TEXT = sendTextRecord(EDITED);
  protected static final SendBinaryRecord EDIT_SEND_DOCUMENT = sendDocumentRecord(EDITED);
  protected static final SendBinaryRecord EDIT_SEND_PHOTO = sendPhotoRecord(EDITED);
  protected static final DeleteMessage DELETE_MESSAGE = new DeleteMessage(MESSAGE_ID, TIMESTAMP);
  protected static final Notification NOTIFICATION = Notification.info(TEXT);
  protected static final kite.core.domain.payload.Error ERROR = Error.err("ERROR");

  private static TgSendBinary tgSendBinary() {
    return TgSendBinaryBuilder.builder()
        .fileId(DOCUMENT_ID)
        .fileName(DOCUMENT_NAME)
        .fileType(DOCUMENT_TYPE)
        .fileSize(DOCUMENT_SIZE)
        .origMessageId(ORIGIN_MESSAGE_ID)
        .messageId(MESSAGE_ID)
        .origChatId(ORIG_CHAT_ID)
        .mode(NEW)
        .timestamp(TIMESTAMP)
        .text(TEXT)
        .build();
  }

  private static TgSendText tgSendText() {
    return TgSendTextBuilder.builder()
        .text(TEXT)
        .origChatId(ORIG_CHAT_ID)
        .origMessageId(ORIGIN_MESSAGE_ID)
        .messageId(MESSAGE_ID)
        .mode(NEW)
        .timestamp(TIMESTAMP)
        .build();
  }

  private static SendTextRecord sendTextRecord(SendText.Mode mode) {
    return new SendTextRecord(TEXT, MESSAGE_ID, mode, TIMESTAMP);
  }

  private static SendBinaryRecord sendDocumentRecord(SendText.Mode mode) {
    return new SendBinaryRecord(
        BASE_URI, DOCUMENT_NAME, DOCUMENT_TYPE, DOCUMENT_SIZE, TEXT, mode, MESSAGE_ID, TIMESTAMP);
  }

  private static SendBinaryRecord sendPhotoRecord(SendText.Mode mode) {
    return new SendBinaryRecord(
        BASE_URI, IMAGE_NAME, IMAGE_TYPE, IMAGE_SIZE, TEXT, mode, MESSAGE_ID, TIMESTAMP);
  }
}

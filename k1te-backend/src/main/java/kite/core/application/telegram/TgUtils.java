/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.application.telegram;

import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.MessageEntity;
import com.pengrad.telegrambot.model.MessageEntity.Type;
import com.pengrad.telegrambot.model.PhotoSize;
import com.pengrad.telegrambot.model.User;
import com.pengrad.telegrambot.model.request.InputMediaDocument;
import com.pengrad.telegrambot.model.request.InputMediaPhoto;
import com.pengrad.telegrambot.model.request.ParseMode;
import com.pengrad.telegrambot.request.BaseRequest;
import com.pengrad.telegrambot.request.CopyMessage;
import com.pengrad.telegrambot.request.EditMessageMedia;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendDocument;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendPhoto;
import com.pengrad.telegrambot.response.BaseResponse;
import java.util.*;
import kite.core.domain.Route;
import kite.core.domain.payload.DeleteMessage;
import kite.core.domain.payload.Error;
import kite.core.domain.payload.Notification;
import kite.core.domain.payload.Payload;
import kite.core.domain.payload.SendBinary;
import kite.core.domain.payload.SendText;
import kite.core.domain.payload.SendText.Mode;

final class TgUtils {

  private TgUtils() {}

  static Optional<? extends BaseRequest<?, ? extends BaseResponse>> toRequest(
      Route destination, Payload payload) {
    var chatId = toTgChatId(destination.raw());
    var request =
        switch (payload) {
          case TgSendBinary p ->
              new CopyMessage(chatId, p.origChatId(), p.origMessageId()).disableNotification(true);
          case TgSendText p ->
              new CopyMessage(chatId, p.origChatId(), p.origMessageId()).disableNotification(true);
          case SendBinary p when p.mode().equals(Mode.NEW) -> sendBinary(chatId, p);
          case SendBinary p when p.mode().equals(Mode.EDITED) -> editBinary(chatId, p);
          case SendText p when p.mode().equals(Mode.NEW) -> new SendMessage(chatId, p.text());
          case SendText p when p.mode().equals(Mode.EDITED) ->
              new EditMessageText(chatId, toTgMessageId(p.messageId()), p.text());
          case DeleteMessage p ->
              new com.pengrad.telegrambot.request.DeleteMessage(
                  chatId, toTgMessageId(p.messageId()));
          case Notification p ->
              new SendMessage(chatId, p.toString()).parseMode(ParseMode.Markdown);
          case Error p -> new SendMessage(chatId, p.toString()).parseMode(ParseMode.Markdown);
          default -> null;
        };
    return Optional.ofNullable(request);
  }

  private static BaseRequest<?, ? extends BaseResponse> sendBinary(Long chatId, SendBinary p) {
    var uri = p.uri().toString();
    var binaryMessage =
        isImage(p.fileType())
            ? new SendPhoto(chatId, uri).caption(p.text())
            : new SendDocument(chatId, uri).caption(p.text());
    return binaryMessage.fileName(p.fileName()).contentType(p.fileType());
  }

  private static BaseRequest<?, ? extends BaseResponse> editBinary(Long chatId, SendBinary p) {
    var uri = /* p instanceof TgEditBinary tg ? tg.fileId() : */ p.uri().toString();
    var media =
        isImage(p.fileType())
            ? new InputMediaPhoto(uri).caption(p.text())
            : new InputMediaDocument(uri).caption(p.text());
    media.fileName(p.fileName()).contentType(p.fileType());
    return new EditMessageMedia(chatId, toTgMessageId(p.messageId()), media);
  }

  private static boolean isImage(String mimeType) {
    mimeType = mimeType.toLowerCase();
    return mimeType.startsWith("image") && !mimeType.contains("gif") && !mimeType.contains("webp");
  }

  public static long toTgChatId(String id) {
    return toLong(id);
  }

  private static long toLong(String id) {
    return Long.parseUnsignedLong(id, Character.MAX_RADIX);
  }

  public static int toTgMessageId(String id) {
    return toInt(id);
  }

  private static int toInt(String id) {
    return Integer.parseUnsignedInt(id, Character.MAX_RADIX);
  }

  static PhotoSize largestPhoto(PhotoSize[] photos) {
    return Arrays.stream(photos).max(Comparator.comparing(PhotoSize::fileSize)).orElseThrow();
  }

  static boolean startsFromCommandEntity(final Message message) {
    final var entities = message.entities();
    if (null != entities && entities.length > 0) {
      final MessageEntity entity = entities[0];
      return Type.bot_command == entity.type();
    }
    return false;
  }

  private static String hashtag(MessageEntity[] entities, String from) {
    for (MessageEntity entity : entities) {
      if (entity.type() == Type.hashtag) {
        int offset = entity.offset() + 1;
        int total = entity.length() + entity.offset();
        return from.substring(offset, total);
      }
    }
    return null;
  }

  static Optional<String> memberIdFromHashTag(final Message replyTo) {
    String result = null;
    if (null != replyTo) {
      if (null != replyTo.entities()) {
        result = hashtag(replyTo.entities(), replyTo.text());
      } else if (null != replyTo.captionEntities()) {
        result = hashtag(replyTo.captionEntities(), replyTo.caption());
      }
    }
    return Optional.ofNullable(result);
  }

  static String userName(User user) {
    var name =
        String.join(
                " ", Objects.toString(user.firstName(), ""), Objects.toString(user.lastName(), ""))
            .trim();
    if (name.isEmpty()) {
      name = user.username();
    }
    return name;
  }
}

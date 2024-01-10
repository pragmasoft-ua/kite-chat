/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.request.PinChatMessage;
import com.pengrad.telegrambot.request.UnpinChatMessage;
import java.util.Optional;
import kite.core.domain.Channel;
import kite.core.domain.Direction;
import kite.core.domain.Member;
import kite.core.domain.UnansweredMessages;
import kite.core.domain.event.MessageRouted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TgUnansweredListener {

  private static final Logger log = LoggerFactory.getLogger(TgUnansweredListener.class);

  final TelegramBot bot;
  final UnansweredMessages unansweredMessages;

  public TgUnansweredListener(TelegramBot bot, UnansweredMessages unansweredMessages) {
    this.bot = bot;
    this.unansweredMessages = unansweredMessages;
  }

  void onMessageRouted(MessageRouted event) {
    Direction direction = event.direction();
    Member.Id memberId = event.member().id();
    Channel channel = event.channel();
    Optional<Integer> maybePinned = this.unansweredMessages.unansweredMessage(memberId);
    if (direction == Direction.FROM_CHANNEL) {
      if (maybePinned.isPresent()) {
        unPin(channel, maybePinned.get());
        this.unansweredMessages.deleteUnansweredMessage(memberId);
      }
    } else {
      if (maybePinned.isEmpty()) {
        var messageId = hostMessageId(event);
        if (null != messageId) {
          pin(channel, messageId);
          this.unansweredMessages.addUnansweredMessage(memberId, messageId);
        }
      }
    }
  }

  private void unPin(Channel channel, int messageId) {
    var channelConnection = TgUtils.toTgChatId(channel.defaultRoute().raw());
    UnpinChatMessage unpinChatMessage =
        new UnpinChatMessage(channelConnection).messageId(messageId);
    var result = this.bot.execute(unpinChatMessage);
    if (result.isOk()) {
      log.error("Unpinned message {} for channel {}", messageId, channel.name());
    } else {
      log.error(
          "({}) {}\nFailed to unpin message {} for channel {}",
          result.errorCode(),
          result.description(),
          messageId,
          channel.name());
    }
  }

  private void pin(Channel channel, int messageId) {
    var channelConnection = TgUtils.toTgChatId(channel.defaultRoute().raw());
    var pinChatMessage = new PinChatMessage(channelConnection, messageId).disableNotification(true);
    var result = this.bot.execute(pinChatMessage);
    if (result.isOk()) {
      log.debug("Pinned message {} for channel {}", messageId, channel.name());
    } else {
      log.error(
          "({}) {}\nFailed to pin message {} for channel {}",
          result.errorCode(),
          result.description(),
          messageId,
          channel.name());
    }
  }

  private Integer hostMessageId(MessageRouted event) {
    for (var entry : event.responses().entrySet()) {
      if (entry.getKey().provider().equals(TgConnector.TG)
          && entry.getValue() instanceof TgAckRecord ack) {
        return ack.tgMessageId();
      }
    }
    return null;
  }
}

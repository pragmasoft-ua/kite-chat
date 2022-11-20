package ua.com.pragmasoft.k1te.router;

public interface IdGenerator {
  String randomStringId(int length);

  default ConversationId randomConversationId() {
    return new ConversationId(randomStringId(12));
  }

  default ChatId randomChatId() {
    return new ChatId(randomStringId(10));
  }

  default MessageId randomMessageId() {
    return new MessageId(randomStringId(16));
  }
}

package ua.com.pragmasoft.k1te.router.infrastructure;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import ua.com.pragmasoft.k1te.router.Chat;
import ua.com.pragmasoft.k1te.router.ChatId;
import ua.com.pragmasoft.k1te.router.ChatRepository;

public class InMemoryChatRepository implements ChatRepository {

  final Map<ChatId, Chat> chats = new ConcurrentHashMap<>();

  @Override
  public Optional<Chat> getChat(ChatId id) {
    return Optional.ofNullable(this.chats.get(id));
  }

  @Override
  public Chat createChat(Chat chat) {
    var existing = this.chats.putIfAbsent(chat.id(), chat);
    if (null != existing) {
      throw new IllegalArgumentException("Chat %s already exists".formatted(chat.id()));
    }
    return chat;
  }

  @Override
  public Chat updateChat(Chat chat) {
    return this.chats.put(chat.id(), chat);
  }

  @Override
  public Chat deleteChat(ChatId id) {
    return this.chats.remove(id);
  }


}

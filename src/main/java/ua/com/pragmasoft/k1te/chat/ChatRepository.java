package ua.com.pragmasoft.k1te.chat;

import java.util.Optional;

public interface ChatRepository {

  Optional<Chat> getChat(ChatId id);

  Chat createChat(Chat chat);

  Chat updateChat(Chat chat);

  Chat deleteChat(ChatId id);

}

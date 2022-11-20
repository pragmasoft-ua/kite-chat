package ua.com.pragmasoft.k1te.router.infrastructure;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import ua.com.pragmasoft.k1te.router.Chat;
import ua.com.pragmasoft.k1te.router.ChatId;
import ua.com.pragmasoft.k1te.router.Conversation;
import ua.com.pragmasoft.k1te.router.ConversationId;
import ua.com.pragmasoft.k1te.router.ConversationRepository;
import ua.com.pragmasoft.k1te.router.IdGenerator;
import ua.com.pragmasoft.k1te.router.Route;

class InMemoryConversationRepository implements ConversationRepository {

  final Map<ConversationId, Conversation> conversations = new HashMap<>();
  final Map<String, ConversationId> secondaryIndex = new HashMap<>();
  final IdGenerator idGenerator;
  // we write potentially even more often than read so no much sense to use ReadWriteLock
  final Lock lock = new ReentrantLock();

  /**
   * @param idGenerator
   */
  public InMemoryConversationRepository(IdGenerator idGenerator) {
    this.idGenerator = idGenerator;
  }

  @Override
  public Conversation findOrCreateForClient(Route client, Chat chat) {
    final String key = chat.id().raw() + ':' + client.uri();
    lock.lock();
    try {
      Conversation conversation = null;
      ConversationId id = this.secondaryIndex.get(key);
      if (null != id) {
        conversation = this.conversations.get(id);
      }
      if (null == conversation || conversation.expired()) {
        id = idGenerator.randomConversationId();
        var now = Instant.now();
        conversation = new Conversation(id, chat.id(), client, chat.defaultRoute(), now, now,
            chat.defaultConversationTimeout());
        this.conversations.put(id, conversation);
        this.secondaryIndex.put(key, id);
      }
      return conversation;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public int deleteConversationsForChat(ChatId id) {
    int deleted = 0;
    this.lock.lock();
    try {
      for (var e : this.conversations.entrySet()) {
        final var conversation = e.getValue();
        if (conversation.chat().equals(id)) {
          this.conversations.entrySet().remove(e);
          this.secondaryIndex.remove(this.conversationKey(conversation));
          deleted++;
        }
      }
    } finally {
      this.lock.unlock();
    }

    return deleted;
  }

  @Override
  public void finishConversation(Conversation conversation) {
    this.lock.lock();
    try {
      this.conversations.remove(conversation.id());
      this.secondaryIndex.remove(this.conversationKey(conversation));
    } finally {
      this.lock.unlock();
    }
  }

  @Override
  public void forwardConversation(Conversation conversation, Route newOperator) {
    this.lock.lock();
    try {
      this.conversations.put(conversation.id(), conversation.forward(newOperator));
    } finally {
      this.lock.unlock();
    }
  }

  String conversationKey(Conversation conversation) {
    return conversation.chat().raw() + ':' + conversation.client().uri();
  }

  @Override
  public Optional<Conversation> getById(ConversationId id) {
    this.lock.lock();
    try {
      return Optional.ofNullable(this.conversations.get(id));
    } finally {
      this.lock.unlock();
    }
  }

}

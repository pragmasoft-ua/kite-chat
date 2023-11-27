/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.ws;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.HashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbMember;

class DynamoDbMemberTest {

  private DynamoDbMember member;
  private DynamoDbMember copy;

  @BeforeEach
  void init() {
    HashMap<String, String> map = new HashMap<>();
    map.put("user", "1");
    Instant time = Instant.now();
    this.member =
        new DynamoDbMember.DynamoDbMemberBuilder()
            .withId("123")
            .withChannelName("my-chat-123")
            .withUserName("my-chat-123")
            .withTgUri("someuri")
            .withTgLastActiveTime(time)
            .withTgLastMessageId("1")
            .withTgLastMessageTime(time)
            .withPinnedMessageId(map)
            .withPeerMemberId("user")
            .withHost(true)
            .build();
    this.copy = member.copy();
  }

  @Test
  void linkTest() {
    assertNotSame(member, copy);
    assertEquals(member, copy);
  }

  @Test
  void updateConnectionTest() {
    assertEquals(member, copy);
    this.member.updateConnection("tg:new", "2", Instant.now().plusSeconds(10));
    assertNotEquals(member, copy);
  }

  @Test
  void updatePeerTest() {
    assertEquals(member, copy);
    this.member.updatePeer("newPeer");
    assertNotEquals(member, copy);
  }

  @Test
  void updatePinnedMessageTest() {
    assertEquals(member, copy);
    DynamoDbMember toMember = new DynamoDbMember.DynamoDbMemberBuilder().withId("user").build();
    this.member.updateUnAnsweredMessage(toMember, "2");
    assertNotEquals(member, copy);
  }
}

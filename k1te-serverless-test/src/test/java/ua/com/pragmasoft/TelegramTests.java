/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.ChatPage;

import java.nio.file.Path;

@Disabled
@Tag("telegram")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TelegramTests extends BaseTest {

  private static final String CHANNEL_NAME = "test-kite-channel";

  @Test
  void uploadFile(){
    System.out.println(kiteChat.uploadFile(Path.of("auth.json")));
  }

  @Test
  @Order(1)
  @DisplayName("/info command before /host")
  void anonymousInfo() {
    String response = telegramChat.sendMessageAndWaitResponse("/info");
    Assertions.assertTrue(response.contains("/join"));
  }

  @Test
  @Order(2)
  @DisplayName("/host command")
  void host() {
    String response = telegramChat.sendMessageAndWaitResponse("/host " + CHANNEL_NAME);
    Assertions.assertTrue(response.contains("channel " + CHANNEL_NAME));
  }

  @Test
  @Order(3)
  @DisplayName("/info command after /host")
  void info() {
    String response = telegramChat.sendMessageAndWaitResponse("/info");
    Assertions.assertTrue(response.contains(CHANNEL_NAME));
  }

  @Test
  @Order(4)
  @DisplayName("/leave command")
  void leave() {
    String response = telegramChat.sendMessageAndWaitResponse("/leave");
    Assertions.assertTrue(response.contains("cannot leave channel"));
  }

  @Test
  @Order(5)
  @DisplayName("/drop command")
  void drop() {
    String response = telegramChat.sendMessageAndWaitResponse("/drop");
    Assertions.assertTrue(response.contains(CHANNEL_NAME));
  }

  @Test
  @Order(6)
  @DisplayName("/help command")
  void help() {
    String response = telegramChat.sendMessageAndWaitResponse("/help");
    Assertions.assertFalse(response.isEmpty());
  }
}

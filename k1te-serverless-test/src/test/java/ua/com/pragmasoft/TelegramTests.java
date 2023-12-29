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
    private static final String BASE_PATH = "src/test/resources";

    @Test
    void uploadFile() {
        telegramChat.uploadFile(Path.of(BASE_PATH,"sample.jpg"));
        telegramChat.uploadFile(Path.of(BASE_PATH,"sample.pdf"));
    }

    @Test
    void uploadPhoto() {
        telegramChat.uploadPhoto(Path.of(BASE_PATH,"sample.jpg"));
    }

    @Test
    @Order(1)
    @DisplayName("/info command before /host")
    void anonymousInfo() {
        telegramChat.sendMessage("/info");
        telegramChat.lastMessage(ChatPage.MessageType.IN)
            .hasText("You don't have any channels at the moment.");
    }

    @Test
    @Order(2)
    @DisplayName("/host command")
    void host() {
        telegramChat.sendMessage("/host " + CHANNEL_NAME);
        telegramChat.lastMessage(ChatPage.MessageType.IN)
            .hasText("Created channel " + CHANNEL_NAME);
    }

    @Test
    @Order(3)
    @DisplayName("/info command after /host")
    void info() {
        telegramChat.sendMessage("/info");
        telegramChat.lastMessage(ChatPage.MessageType.IN)
            .hasText("You are a Host of the " + CHANNEL_NAME + " channel.");
    }

    @Test
    @Order(4)
    @DisplayName("/leave command")
    void leave() {
        telegramChat.sendMessage("/leave");
        telegramChat.lastMessage(ChatPage.MessageType.IN)
            .hasText("Host member cannot leave channel.");
    }

    @Test
    @Order(5)
    @DisplayName("/drop command")
    void drop() {
        telegramChat.sendMessage("/drop");
        telegramChat.lastMessage(ChatPage.MessageType.IN)
            .hasText("You dropped channel " + CHANNEL_NAME);
    }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;

import com.microsoft.playwright.*;

import java.nio.file.Path;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

@Tag("telegram-to-telegram")
class TelegramToTelegramTests extends BaseTest {
    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext telegramContext;
    private static TelegramChatPage hostChat;
    private static TelegramChatPage memberChat;

    @BeforeAll
    static void initBrowser() {
        playwright = Playwright.create();
        browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

        telegramContext = browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
        telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);

        hostChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_HOST_CHAT_TITLE);
        memberChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_MEMBER_CHAT_TITLE);


        sendTextAndWaitResponse(hostChat, "/host " + CHANNEL_NAME,
            "Created channel " + CHANNEL_NAME);

        sendTextAndWaitResponse(memberChat, "/join " + CHANNEL_NAME,
            "You joined channel " + CHANNEL_NAME);
    }

    @Test
    @DisplayName("Member sends a text message to the Host")
    void member_sends_text_message_to_host() {
        String greet = "Hello!";
        sendText(memberChat, greet);
        assertIncomingText(hostChat, greet);
    }

    @ParameterizedTest(name = "Member sends a file with {argumentsWithNames} to the Host")
    @DisplayName("Member sends files to the Host")
    @ValueSource(strings = {"csv", "docx", "json", "pdf", "txt", "zip"})
    void member_sends_files_to_host(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        String fileName = sendFile(memberChat, path);
        assertIncomingFile(hostChat, fileName);
    }

    @ParameterizedTest(name = "Member sends a photo with {argumentsWithNames} to the Host")
    @DisplayName("Member sends photos to the Host")
    @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
    void member_sends_photos_to_host(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendPhoto(memberChat, path);
        assertIncomingPhoto(hostChat);
    }

    @Test
    @DisplayName("Host sends a text message to the Member")
    void host_sends_text_message_to_member() {
        String greet = "Hello!";
        sendText(hostChat, greet);
        assertIncomingText(memberChat, greet);
    }

    @ParameterizedTest(name = "Host sends a file with {argumentsWithNames} to the Member")
    @DisplayName("Host sends files to the Member")
    @ValueSource(strings = {"csv", "docx", "json", "pdf", "txt", "zip"})
    void host_sends_files_to_member(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        String fileName = sendFile(hostChat, path);
        assertIncomingFile(memberChat, fileName);
    }

    @ParameterizedTest(name = "Host sends a photo with {argumentsWithNames} to the Member")
    @DisplayName("Host sends photos to the member")
    @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
    void host_sends_photos_to_member(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendPhoto(hostChat, path);
        assertIncomingPhoto(memberChat);
    }

    @Test
    @DisplayName("Emulate chatting between Host and Member")
    void emulate_chatting() {
        String memberGreet = "Hello!";
        sendText(memberChat, memberGreet);
        assertIncomingText(hostChat, memberGreet);

        String hostGreet = "Hi!";
        sendText(hostChat, hostGreet);
        assertIncomingText(memberChat, hostGreet);

        String hostQuestion = "How can I help you?";
        sendText(hostChat, hostQuestion);
        assertIncomingText(memberChat, hostQuestion);

        String memberRequestText = "I don't understand what it means. Here is a screenshot";
        sendText(memberChat, memberRequestText);
        assertIncomingText(hostChat, memberRequestText);

        sendPhoto(memberChat, Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + "png"));
        assertIncomingPhoto(hostChat);

        String hostAnswerText = "Here is a pdf instruction how to solve this";
        sendText(hostChat, hostAnswerText);
        assertIncomingText(memberChat, hostAnswerText);

        String fileName = sendFile(hostChat, Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + "pdf"));
        assertIncomingFile(memberChat, fileName);

        String memberThanksText = "Thank you, It helped";
        sendText(memberChat, memberThanksText);
        assertIncomingText(hostChat, memberThanksText);

        String hostWelcomeText = "You are welcome";
        sendText(hostChat, hostWelcomeText);
        assertIncomingText(memberChat, hostWelcomeText);
    }

    private static void sendText(TelegramChatPage chat, String text) {
        chat.getPage().bringToFront();
        chat.sendMessage(text);
    }

    private static String sendFile(TelegramChatPage chat, Path path) {
        chat.getPage().bringToFront();
        return chat.uploadFile(path);
    }

    private static void sendPhoto(TelegramChatPage chat, Path path) {
        chat.getPage().bringToFront();
        chat.uploadPhoto(path);
    }

    private static void sendTextAndWaitResponse(TelegramChatPage chat, String text,
        String response) {
        chat.getPage().bringToFront();
        chat.sendMessage(text);
        chat.lastMessage(IN).hasText(response);
    }

    private static void assertIncomingText(TelegramChatPage chat, String expected) {
        chat.getPage().bringToFront();
        chat.lastMessage(IN).hasText(expected);
    }

    private static void assertIncomingFile(TelegramChatPage chat, String expected) {
        chat.getPage().bringToFront();
        chat.lastMessage(IN).hasFile(expected);
    }

    private static void assertIncomingPhoto(TelegramChatPage chat) {
        chat.getPage().bringToFront();
        chat.lastMessage(IN).isPhoto();
    }

    @AfterAll
    static void closeBrowser() {
        sendText(memberChat, "/leave");
        sendText(hostChat, "/drop");

        telegramContext.close();
        browser.close();
        playwright.close();
    }
}

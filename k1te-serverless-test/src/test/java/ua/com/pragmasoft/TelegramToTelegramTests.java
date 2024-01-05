/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;

import java.nio.file.Path;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
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
            new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));
        telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);

        hostChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_HOST_CHAT_TITLE);
        memberChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_MEMBER_CHAT_TITLE);


        sendTextAndVerifyResponse(hostChat, "/host " + TELEGRAM_CHANNEL_NAME,
            "Created channel " + TELEGRAM_CHANNEL_NAME);

        sendTextAndVerifyResponse(memberChat, "/join " + TELEGRAM_CHANNEL_NAME,
            "You joined channel " + TELEGRAM_CHANNEL_NAME);
    }

    @Test
    @DisplayName("Member sends a text message to the Host")
    void member_sends_text_message_to_host() {
        sendTextAndVerify(memberChat, hostChat, "Hello!");
    }

    @ParameterizedTest(name = "Member sends a file with {argumentsWithNames} to the Host")
    @DisplayName("Member sends files to the Host")
    @ValueSource(strings = {"csv", "docx", "json", "pdf", "txt", "zip"})
    void member_sends_files_to_host(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendFileAndVerify(memberChat, hostChat, path);
    }

    @ParameterizedTest(name = "Member sends a photo with {argumentsWithNames} to the Host")
    @DisplayName("Member sends photos to the Host")
    @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
    void member_sends_photos_to_host(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendPhotoAndVerify(memberChat, hostChat, path);
    }

    @Test
    @DisplayName("Host sends a text message to the Member")
    void host_sends_text_message_to_member() {
        sendTextAndVerify(hostChat, memberChat, "Hello!");
    }

    @ParameterizedTest(name = "Host sends a file with {argumentsWithNames} to the Member")
    @DisplayName("Host sends files to the Member")
    @ValueSource(strings = {"csv", "docx", "json", "pdf", "txt", "zip"})
    void host_sends_files_to_member(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendFileAndVerify(hostChat, memberChat, path);
    }

    @ParameterizedTest(name = "Host sends a photo with {argumentsWithNames} to the Member")
    @DisplayName("Host sends photos to the member")
    @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
    void host_sends_photos_to_member(String type) {
        Path path = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
        sendPhotoAndVerify(hostChat, memberChat, path);
    }

    @Test
    @DisplayName("Emulate chatting between Host and Member")
    void emulate_chatting() {
        sendTextAndVerify(memberChat, hostChat, "Hello!");
        sendTextAndVerify(hostChat, memberChat, "Hi!");
        sendTextAndVerify(hostChat, memberChat, "How can I help you?");
        sendTextAndVerify(memberChat, hostChat,
            "I don't understand what it means. Here is a screenshot");
        sendPhotoAndVerify(memberChat, hostChat,
            Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + "png"));
        sendTextAndVerify(hostChat, memberChat,
            "Here is a pdf instruction how to solve this problem");
        sendFileAndVerify(hostChat, memberChat,
            Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + "pdf"));
        sendTextAndVerify(memberChat, hostChat, "Thank you, It helped");
        sendTextAndVerify(hostChat, memberChat, "You are welcome");
    }

    @AfterAll
    static void closeBrowser() {
        sendTextAndVerifyResponse(memberChat, "/leave",
            "You left channel " + TELEGRAM_CHANNEL_NAME);
        sendTextAndVerifyResponse(hostChat, "/drop",
            "You dropped channel " + TELEGRAM_CHANNEL_NAME);

        telegramContext.close();
        browser.close();
        playwright.close();
    }
}

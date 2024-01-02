/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.FilePayload;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import ua.com.pragmasoft.chat.ChatPage;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipFile;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;

@Tag("kite-to-telegram")
class KiteToTelegramTests {
    private static final String DEFAULT_TELEGRAM_CHAT_TITLE = "www.k1te.chat";
    private static final String DEFAULT_KITE_URL = "https://www.k1te.chat/test";

    private static final String BASE_RESOURCE_PATH = "src/test/resources";
    private static final String BASE_FILE_NAME = "sample.";

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext kiteContext;
    private static KiteChatPage kiteChat;
    private static BrowserContext telegramContext;
    private static TelegramChatPage telegramChat;


    @BeforeAll
    static void initBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
    }

    @BeforeEach
    void initChats() {
        String telegramChatTitle = System.getProperty("chat-title", DEFAULT_TELEGRAM_CHAT_TITLE);
        String kiteUrl = System.getProperty("kite-url", DEFAULT_KITE_URL);

        kiteContext = browser.newContext();
        kiteChat = KiteChatPage.of(kiteContext.newPage(), kiteUrl);

        telegramContext =
            browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
        telegramChat = TelegramChatPage.of(telegramContext.newPage(), telegramChatTitle);
    }

    @Test
    @DisplayName("User sends a text message")
    void sending_text_message_to_host() {
        String text = "hello!";
        kiteChat.sendMessage(text);
        telegramChat.lastMessage(IN)
            .hasText(text);
    }

    @ParameterizedTest(name = "User sends a supported file with {argumentsWithNames} to the Host")
    @ValueSource(strings = {"pdf", "zip"})
    void sending_supported_files_to_host(String type) {
        String uploadedFileName =
            kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
        telegramChat.lastMessage(IN)
            .hasFile(uploadedFileName);
    }

    @ParameterizedTest(name = "User sends an unsupported file with {argumentsWithNames} to the Host, should be converted into zip")
    @ValueSource(strings = {"docx", "txt", "csv", "json"})
    void sending_unsupported_files_to_host(String type) {
        String uploadedFileName =
            kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
        telegramChat.lastMessage(IN)
            .hasFile(uploadedFileName.replaceAll("\\..\\w+", ".zip"));
    }

    @Test
    void sending_large_file_to_host() throws IOException {
        Path file = null;
        try {
            file = Files.write(Path.of(BASE_RESOURCE_PATH, "test-sample.zip"),
                new byte[21_000_000]);
            kiteChat.uploadFile(file);
            kiteChat.hasErrorMessage("size exceeds 20.00 MB limit");
        } finally {
            if (file != null)
                Files.deleteIfExists(file);
        }
    }

    //webp currently not supported
    @ParameterizedTest(name = "User sends a supported image with {argumentsWithNames} to the Host")
    @ValueSource(strings = {"jpg", "png", "gif"})
    void sending_supported_photos_to_host(String type) {
        kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
        telegramChat.lastMessage(IN)
            .isPhoto();
    }

    //tiff currently is shown as regular file
    @ParameterizedTest(name = "User sends an unsupported image with {argumentsWithNames} to the Host, should be converted into zip")
    @ValueSource(strings = {"bmp"})
    void sending_unsupported_photos_to_host(String type) {
        kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
        telegramChat.lastMessage(IN)
            .hasFile("sample.zip");
    }

    @AfterEach
    void closeChats() {
        telegramContext.close();
        kiteContext.close();
    }

    @AfterAll
    static void closeBrowser() {
        browser.close();
        playwright.close();
    }
}

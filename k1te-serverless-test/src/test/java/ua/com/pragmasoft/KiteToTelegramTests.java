/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;

import com.microsoft.playwright.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage.TelegramChatMessage;

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
    String telegramChatTitle = System.getProperty("chat-title", DEFAULT_TELEGRAM_CHAT_TITLE);
    String kiteUrl = System.getProperty("kite-url", DEFAULT_KITE_URL);

    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));

    telegramContext =
        browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
    telegramChat = TelegramChatPage.of(telegramContext.newPage(), telegramChatTitle);

    kiteContext = browser.newContext();
    kiteChat = KiteChatPage.of(kiteContext.newPage(), kiteUrl);
  }

  @Test
  @DisplayName("User sends a text message")
  void sending_text_message_to_host() {
    String text = "hello!";
    kiteChat.sendMessage(text);
    telegramChat.lastMessage(IN).hasText(text);
  }

  @Test
  @DisplayName("User sends a large text message that exceeds limit")
  void send_large_text_message_to_host() {
    int size = 4 * 1024;
    kiteChat.sendMessage("a".repeat(size + 1));
    kiteChat.hasErrorMessage("Text message size exceeds 4.00 KB limit");
  }

  @ParameterizedTest(name = "User sends a supported file with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"pdf", "zip"})
  @DisplayName("User sends supported files to Host")
  void send_supported_files_to_host(String type) {
    String uploadedFileName =
        kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).hasFile(uploadedFileName);
  }

  @ParameterizedTest(
      name =
          "User sends an unsupported file with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"docx", "txt", "csv", "json"})
  @DisplayName("User sends unsupported files to Host")
  void send_unsupported_files_to_host(String type) {
    String uploadedFileName =
        kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).hasFile(uploadedFileName.replaceAll("\\..\\w+", ".zip"));
  }

  @Test
  @DisplayName("User sends an exceeding file to Host")
  void send_large_file_to_host() throws IOException {
    Path file = null;
    try {
      file = Files.write(Path.of(BASE_RESOURCE_PATH, "test-sample.zip"), new byte[21_000_000]);
      kiteChat.uploadFile(file);
      kiteChat.hasErrorMessage("size exceeds 20.00 MB limit");
    } finally {
      if (file != null) Files.deleteIfExists(file);
    }
  }

  // webp currently not supported
  @ParameterizedTest(name = "User sends a supported image with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"jpg", "png", "gif"})
  @DisplayName("User sends supported images to Host")
  void send_supported_photos_to_host(String type) {
    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).isPhoto();
  }

  // tiff currently is shown as regular file
  @ParameterizedTest(
      name = "User sends an unsupported image with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"bmp"})
  @DisplayName("User sends unsupported files to Host")
  void send_unsupported_photos_to_host(String type) {
    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).hasFile("sample.zip");
  }

  @Test
  @DisplayName("User reconnects to the chat and recovers chat history")
  void reconnect_and_recover_history() {
    String first = "first";
    kiteChat.sendMessage(first);
    telegramChat.lastMessage(IN).hasText(first);

    String second = "second";
    telegramChat.sendMessage(second);
    kiteChat.lastMessage(IN).hasText(second);

    String uploadedFile = kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, "sample.pdf"));
    telegramChat.lastMessage(IN).hasFile(uploadedFile);

    telegramChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample.jpg"));
    kiteChat.lastMessage(IN).isPhoto();

    int messagesBefore = kiteChat.messagesCount();
    kiteChat.getPage().reload();
    kiteChat.getPage().locator("#kite-toggle").click();
    kiteChat.getPage().waitForTimeout(1000);
    int messagesAfter = kiteChat.messagesCount();

    Assertions.assertEquals(messagesBefore, messagesAfter);
  }

  @Test
  @DisplayName("Host sends a text message to User")
  void send_text_message_to_user() {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String hostText = "Hello! I'm your Host";
    telegramChat.sendMessage(hostText);
    kiteChat.lastMessage(IN).hasText(hostText);
  }

  @ParameterizedTest(name = "Host sends a file with {argumentsWithNames} to the User")
  @ValueSource(strings = {"pdf", "zip", "docx", "txt", "csv", "json"})
  @DisplayName("Host sends files to User")
  void send_file_to_user(String type) {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String uploadedFile = telegramChat.uploadFile(Path.of(BASE_RESOURCE_PATH, "sample." + type));
    kiteChat.lastMessage(IN).hasFile(uploadedFile);
  }

  @ParameterizedTest(name = "Host sends an image with {argumentsWithNames} to the User")
  @ValueSource(strings = {"jpg", "png", "gif", "webp", "bmp"})
  @DisplayName("Host sends images to User")
  void send_image_to_user(String type) {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    telegramChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample." + type));
    kiteChat.lastMessage(IN).isPhoto();
  }

  @Test
  @DisplayName("Host replies to User's message")
  void host_replies_to_user() {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    TelegramChatMessage message = telegramChat.lastMessage(IN);
    message.hasText(userText);

    String hostText = "Hello! I'm Host";
    telegramChat.replyMessage(message, hostText);
    kiteChat.lastMessage(IN).hasText(hostText);
  }

  @Test
  @DisplayName("Host replies to a specific User's message")
  void host_replies_to_specific_user() {
    String kiteUrl = System.getProperty("kite-url", DEFAULT_KITE_URL);
    try (BrowserContext browserContext = browser.newContext()) {
      KiteChatPage secondKiteChat = KiteChatPage.of(browserContext.newPage(), kiteUrl);

      String firstUserHelloText = "Hello, I'm First User";
      kiteChat.sendMessage(firstUserHelloText);
      TelegramChatMessage message = telegramChat.lastMessage(IN);
      message.hasText(firstUserHelloText);
      ElementHandle firstMessage = message.element();

      String firstUserText = "I need your support";
      kiteChat.sendMessage(firstUserText);
      telegramChat.lastMessage(IN).hasText(firstUserText);

      String secondUserText = "Hello, I'm Second User";
      secondKiteChat.sendMessage(secondUserText);
      telegramChat.lastMessage(IN).hasText(secondUserText);

      String hostText = "Hello, First User. How can I help you?";
      telegramChat.replyMessage(firstMessage, hostText);
      kiteChat.lastMessage(IN).hasText(hostText);
    }
  }

  @AfterAll
  static void closeBrowser() {
    telegramContext.close();
    kiteContext.close();

    browser.close();
    playwright.close();
  }
}

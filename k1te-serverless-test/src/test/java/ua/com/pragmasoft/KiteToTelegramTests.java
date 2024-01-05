/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;

import com.microsoft.playwright.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.kite.KiteChatPage.KiteChatMessage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage.TelegramChatMessage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

@Tag("kite-to-telegram")
class KiteToTelegramTests extends BaseTest {
  private static Playwright playwright;
  private static Browser browser;
  private static BrowserContext kiteContext;
  private static KiteChatPage kiteChat;
  private static BrowserContext telegramContext;
  private static TelegramChatPage telegramChat;

  @BeforeAll
  static void initBrowser() {
    playwright = Playwright.create();
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

    telegramContext =
        browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));
    telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);
    telegramChat =
        new TelegramClientPage(telegramContext.newPage()).openChat(TELEGRAM_HOST_CHAT_TITLE);

    kiteContext = browser.newContext();
    kiteContext.setDefaultTimeout(DEFAULT_TIMEOUT);
    kiteChat = KiteChatPage.of(kiteContext.newPage(), KITE_CHAT_URL_WITH_CHANNEL);

    sendTextAndVerifyResponse(
        telegramChat, "/host " + TELEGRAM_CHANNEL_NAME, "Created channel " + TELEGRAM_CHANNEL_NAME);
  }

  @Test
  @DisplayName("User sends a text message")
  void user_sends_text_message_to_host() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello!");
  }

  @Test
  @DisplayName("User sends a large text message that exceeds limit")
  void user_sends_large_text_message_to_host() {
    int size = 4 * 1024;
    kiteChat.sendMessage("a".repeat(size + 1));
    kiteChat.hasErrorMessage("Text message size exceeds 4.00 KB limit");
  }

  @ParameterizedTest(name = "User sends a supported file with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"pdf", "zip"})
  @DisplayName("User sends supported files to Host")
  void user_sends_supported_files_to_host(String type) {
    sendFileAndVerify(kiteChat, telegramChat, buildPath(type));
  }

  @ParameterizedTest(
      name =
          "User sends an unsupported file with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"docx", "txt", "csv", "json"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_files_to_host(String type) {
    String uploadedFileName = kiteChat.uploadFile(buildPath(type));
    telegramChat.lastMessage(IN).hasFile(uploadedFileName.replaceAll("\\..\\w+", ".zip"));
  }

  @Test
  @DisplayName("User sends an exceeding file to Host")
  void user_sends_large_file_to_host() throws IOException {
    Path file = null;
    try {
      file = Files.write(Path.of(BASE_RESOURCE_PATH, "test-sample.zip"), new byte[21_000_000]);
      kiteChat.uploadFile(file);
      kiteChat.hasErrorMessage("size exceeds 20.00 MB limit");
    } finally {
      if (file != null) Files.deleteIfExists(file);
    }
  }

  @ParameterizedTest(name = "User sends a supported image with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"jpg", "png", "gif"}) // webp currently not supported on client
  @DisplayName("User sends supported images to Host")
  void user_sends_supported_photos_to_host(String type) {
    sendPhotoAndVerify(kiteChat, telegramChat, buildPath(type));
  }

  @ParameterizedTest(
      name =
          "User sends an unsupported image with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"bmp", "tiff"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_photos_to_host(String type) {
    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).hasFile(BASE_FILE_NAME + "zip");
  }

  @Test
  @DisplayName("User edits a sent message")
  void user_edits_sent_message() {
    ChatMessage message = sendTextAndVerify(kiteChat, telegramChat, "Hello!, I'm Alev").snapshot();
    sendTextAndVerify(kiteChat, telegramChat, "I need your help");

    kiteChat.editMessage(message, "Hello!, I'm Alex!");
    // TODO: 03.01.2024 Verify changed message in Telegram
  }

  @Test
  @DisplayName("User deletes a sent text message")
  void user_deletes_sent_text_message() {
    ChatMessage message =
        sendTextAndVerify(kiteChat, telegramChat, "Hello! I need help").snapshot();
    sendTextAndVerify(kiteChat, telegramChat, "Are you here?");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent file message")
  void user_deletes_sent_file_message() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello! I need help");
    ChatMessage message = sendFileAndVerify(kiteChat, telegramChat, buildPath("pdf")).snapshot();
    sendTextAndVerify(kiteChat, telegramChat, "Did you receive file?");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent photo message")
  void user_deletes_sent_photo_message() {
    ChatMessage message = sendPhotoAndVerify(kiteChat, telegramChat, buildPath("jpg")).snapshot();
    sendTextAndVerify(kiteChat, telegramChat, "Hello! I need help");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User reconnects to the chat and recovers chat history")
  void user_reconnects_and_recover_history() {
    String firstText = "first";
    ChatMessage firstMessage = sendTextAndVerify(kiteChat, telegramChat, firstText).snapshot();

    String secondText = "second";
    sendTextAndVerify(telegramChat, kiteChat, secondText);
    KiteChatMessage secondMessage = kiteChat.lastMessage(IN).hasText(secondText).snapshot();

    sendPhotoAndVerify(telegramChat, kiteChat, buildPath("png"));
    KiteChatMessage photoMessage = kiteChat.lastMessage(IN).isPhoto().snapshot();

    ChatMessage fileMessage =
        sendFileAndVerify(kiteChat, telegramChat, buildPath("zip")).snapshot();

    kiteChat.getPage().reload();
    kiteChat.getPage().locator("#kite-toggle").click();
    kiteChat.waitFor(1000);

    firstMessage.hasText(firstText);
    secondMessage.hasText(secondText);
    photoMessage.isPhoto();
    fileMessage.hasFile(buildPath("zip").getFileName().toString());
  }

  @Test
  @DisplayName("Host sends a text message to User")
  void host_sends_text_message_to_user() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    sendTextAndVerify(telegramChat, kiteChat, "Hello! I'm your Host");
  }

  @ParameterizedTest(name = "Host sends a file with {argumentsWithNames} to the User")
  @ValueSource(strings = {"pdf", "zip", "docx", "txt", "csv", "json"})
  @DisplayName("Host sends files to User")
  void host_sends_file_to_user(String type) {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    sendFileAndVerify(telegramChat, kiteChat, buildPath(type));
  }

  @ParameterizedTest(name = "Host sends an image with {argumentsWithNames} to the User")
  @ValueSource(strings = {"jpg", "png", "webp", "bmp"}) // gif is sent as a mp4 file
  @DisplayName("Host sends images to User")
  void host_sends_photo_to_user(String type) {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    sendPhotoAndVerify(telegramChat, kiteChat, buildPath(type));
  }

  @Test
  @DisplayName("Host replies to User's message")
  void host_replies_to_user() {
    String userText = "Hello, I'm User";
    sendTextAndVerify(kiteChat, telegramChat, userText);
    TelegramChatMessage message = telegramChat.lastMessage(IN).hasText(userText).snapshot();

    String hostText = "Hello! I'm Host";
    telegramChat.replyMessage(message, hostText);
    kiteChat.lastMessage(IN).hasText(hostText);
  }

  @Test
  @DisplayName("Host edits a sent message")
  void host_edits_sent_message() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    ChatMessage message = sendTextAndVerify(telegramChat, kiteChat, "Hello! I'm Hos").snapshot();
    sendTextAndVerify(telegramChat, kiteChat, "How can I help you?");

    String correctHostText = "Hello! I'm Host!";
    telegramChat.editMessage(message, correctHostText);
    // TODO: 02.01.2024 check updated message in kite chat
  }

  @Test
  @DisplayName("Host deletes a sent text message")
  void host_deletes_sent_text_message() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    ChatMessage message = sendTextAndVerify(telegramChat, kiteChat, "Hello! I'm Hos").snapshot();
    sendTextAndVerify(telegramChat, kiteChat, "How can I help you?");

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent file message")
  void host_deletes_sent_file_message() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    ChatMessage message = sendFileAndVerify(telegramChat, kiteChat, buildPath("zip")).snapshot();
    sendTextAndVerify(telegramChat, kiteChat, "How can I help you?");

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent photo message")
  void host_deletes_sent_photo_message() {
    sendTextAndVerify(kiteChat, telegramChat, "Hello, I'm User");
    ChatMessage message = sendPhotoAndVerify(telegramChat, kiteChat, buildPath("png")).snapshot();
    sendTextAndVerify(telegramChat, kiteChat, "How can I help you?");

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @AfterEach
  void waiter() {
    kiteChat.waitFor(500);
    telegramChat.waitFor(500);
  }

  @AfterAll
  static void closeBrowser() {
    sendTextAndVerifyResponse(
        telegramChat, "/drop", "You dropped channel " + TELEGRAM_CHANNEL_NAME);
    telegramContext.close();
    kiteContext.close();

    browser.close();
    playwright.close();
  }

  /**
   * This nested test class is used to run host_replies_to_specific_user() test separately from the
   * main tests because it involves the other instance of KiteChat.
   */
  @Nested
  class HostReplyToUserTest {

    @Test
    @DisplayName("Host replies to a specific User's message")
    void host_replies_to_specific_user() {
      try (BrowserContext browserContext = browser.newContext()) {
        KiteChatPage secondKiteChat =
            KiteChatPage.of(browserContext.newPage(), KITE_CHAT_URL_WITH_CHANNEL);

        String firstUserHelloText = "Hello, I'm First User";
        sendTextAndVerify(kiteChat, telegramChat, firstUserHelloText);
        TelegramChatMessage message =
            telegramChat.lastMessage(IN).hasText(firstUserHelloText).snapshot();

        sendTextAndVerify(kiteChat, telegramChat, "I need your support");

        sendTextAndVerify(secondKiteChat, telegramChat, "Hello, I'm Second User");

        String hostText = "Hello, First User. How can I help you?";
        telegramChat.replyMessage(message, hostText);
        kiteChat.lastMessage(IN).hasText(hostText);
      }
    }
  }
}

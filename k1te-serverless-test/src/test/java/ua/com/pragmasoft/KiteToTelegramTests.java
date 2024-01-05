/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;
import static ua.com.pragmasoft.chat.ChatPage.MessageType.OUT;

import com.microsoft.playwright.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.kite.KiteChatPage.KiteChatMessage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage.TelegramChatMessage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

@Tag("kite-to-telegram")
class KiteToTelegramTests extends BaseTest{
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

    telegramContext = browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));
    telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);
    telegramChat = new TelegramClientPage(telegramContext.newPage())
        .openChat(TELEGRAM_HOST_CHAT_TITLE);

    kiteContext = browser.newContext();
    kiteContext.setDefaultTimeout(DEFAULT_TIMEOUT);
    kiteChat = KiteChatPage.of(kiteContext.newPage(), KITE_CHAT_URL_WITH_CHANNEL);

    sendTextAndVerifyResponse(telegramChat, "/host " + TELEGRAM_CHANNEL_NAME,
        "Created channel " + TELEGRAM_CHANNEL_NAME);
  }

  @Test
  @DisplayName("User sends a text message")
  void user_sends_text_message_to_host() {
    sendTextAndVerify(kiteChat,telegramChat,"Hello!");
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
    Path file = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
    sendFileAndVerify(kiteChat,telegramChat, file);
  }

  @ParameterizedTest(
      name =
          "User sends an unsupported file with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"docx", "txt", "csv", "json"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_files_to_host(String type) {
    String uploadedFileName =
        kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
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
  @ValueSource(strings = {"jpg", "png", "gif"})// webp currently not supported on client
  @DisplayName("User sends supported images to Host")
  void user_sends_supported_photos_to_host(String type) {
    Path photo = Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
    sendPhotoAndVerify(kiteChat,telegramChat, photo);
  }


  @ParameterizedTest(
      name =
          "User sends an unsupported image with {argumentsWithNames} to the Host, should be"
              + " converted into zip")
  @ValueSource(strings = {"bmp","tiff"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_photos_to_host(String type) {
    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    telegramChat.lastMessage(IN).hasFile(BASE_FILE_NAME+"zip");
  }

  @Test
  @DisplayName("User edits a sent message")
  void user_edits_sent_message() {
    String wrongText = "Hello!, I'm Alev";
    kiteChat.sendMessage(wrongText);
    telegramChat.lastMessage(IN).hasText(wrongText);
    KiteChatMessage message = kiteChat.lastMessage(OUT).snapshot();

    String helpText = "I need your help";
    kiteChat.sendMessage(helpText);
    telegramChat.lastMessage(IN).hasText(helpText);

    kiteChat.editMessage(message, "Hello!, I'm Alex!");
    // TODO: 03.01.2024 Verify changed message in Telegram
  }

  @Test
  @DisplayName("User deletes a sent text message")
  void user_deletes_sent_text_message() {
    String text = "Hello! I need help";
    kiteChat.sendMessage(text);
    telegramChat.lastMessage(IN).hasText(text);

    String deletableMessage = "Another message";
    kiteChat.sendMessage(deletableMessage);
    telegramChat.lastMessage(IN).hasText(deletableMessage);
    KiteChatMessage message = kiteChat.lastMessage(OUT).snapshot();

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent file message")
  void user_deletes_sent_file_message() {
    String text = "Hello! I need help";
    kiteChat.sendMessage(text);
    telegramChat.lastMessage(IN).hasText(text);

    String uploadedFile = kiteChat.uploadFile(Path.of(BASE_RESOURCE_PATH, "sample.pdf"));
    telegramChat.lastMessage(IN).hasFile(uploadedFile);
    KiteChatMessage message = kiteChat.lastMessage(OUT).snapshot();

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent photo message")
  void user_deletes_sent_photo_message() {
    String text = "Hello! I need help";
    kiteChat.sendMessage(text);
    telegramChat.lastMessage(IN).hasText(text);

    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample.jpg"));
    telegramChat.lastMessage(IN).isPhoto();
    KiteChatMessage message = kiteChat.lastMessage(OUT).snapshot();

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User reconnects to the chat and recovers chat history")
  void user_reconnects_and_recover_history() {
    String first = "first";
    kiteChat.sendMessage(first);
    telegramChat.lastMessage(IN).hasText(first);

    String second = "second";
    telegramChat.sendMessage(second);
    kiteChat.lastMessage(IN).hasText(second);

    telegramChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample.jpg"));
    kiteChat.lastMessage(IN).isPhoto();

    int messagesBefore = kiteChat.messagesCount();
    kiteChat.getPage().reload();
    kiteChat.getPage().locator("#kite-toggle").click();
    kiteChat.getPage().waitForTimeout(1500);
    int messagesAfter = kiteChat.messagesCount();

    Assertions.assertEquals(messagesBefore, messagesAfter);
  }

  @Test
  @DisplayName("Host sends a text message to User")
  void host_sends_text_message_to_user() {
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
  void host_sends_file_to_user(String type) {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String uploadedFile = telegramChat.uploadFile(Path.of(BASE_RESOURCE_PATH, "sample." + type));
    kiteChat.lastMessage(IN).hasFile(uploadedFile);
  }

  @ParameterizedTest(name = "Host sends an image with {argumentsWithNames} to the User")
  @ValueSource(strings = {"jpg", "png", "webp", "bmp"}) // gif is sent as a mp4 file
  @DisplayName("Host sends images to User")
  void host_sends_photo_to_user(String type) {
    String userText = "Hello, I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    telegramChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample." + type));
    kiteChat.waitFor(500); // We can not verify specific photo as we do with files by their names
    // and sometimes last sent photo can be estimated
    // as a new one that make this test useless, so wee need to wait to prevent such a behaviour
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
  @DisplayName("Host edits a sent message")
  void host_edits_message() {
    String userText = "Hello! I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String wrongHostText = "Hello! I'm Hos";
    telegramChat.sendMessage(wrongHostText);
    kiteChat.lastMessage(IN).hasText(wrongHostText);

    TelegramChatMessage message = telegramChat.lastMessage(OUT).snapshot();
    String correctHostText = "Hello! I'm Host!";
    telegramChat.editMessage(message, correctHostText);
    // TODO: 02.01.2024 check updated message in kite chat
  }

  @Test
  @DisplayName("Host deletes a sent text message")
  void host_deletes_sent_text_message() {
    String userText = "Hello! I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String wrongHostText = "Hello! I'm Hos";
    telegramChat.sendMessage(wrongHostText);
    kiteChat.lastMessage(IN).hasText(wrongHostText);
    TelegramChatMessage message = telegramChat.lastMessage(OUT).snapshot();

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent file message")
  void host_deletes_sent_file_message() {
    String userText = "Hello! I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    String uploadedFile = telegramChat.uploadFile(Path.of(BASE_RESOURCE_PATH, "sample.zip"));
    kiteChat.lastMessage(IN).hasFile(uploadedFile);
    TelegramChatMessage message = telegramChat.lastMessage(OUT).snapshot();

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent photo message")
  void host_deletes_sent_photo_message() {
    String userText = "Hello! I'm User";
    kiteChat.sendMessage(userText);
    telegramChat.lastMessage(IN).hasText(userText);

    telegramChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, "sample.png"));
    kiteChat.lastMessage(IN).isPhoto();
    TelegramChatMessage message = telegramChat.lastMessage(OUT).snapshot();

    telegramChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @AfterEach
  void waiter() {
    kiteChat.getPage().waitForTimeout(500);
    telegramChat.getPage().waitForTimeout(500);
  }

  @AfterAll
  static void closeBrowser() {
    sendTextAndVerifyResponse(telegramChat, "/drop",
        "You dropped channel " + TELEGRAM_CHANNEL_NAME);
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
        KiteChatPage secondKiteChat = KiteChatPage.of(browserContext.newPage(), KITE_CHAT_URL_WITH_CHANNEL);

        String firstUserHelloText = "Hello, I'm First User";
        kiteChat.sendMessage(firstUserHelloText);
        TelegramChatMessage message =
            telegramChat.lastMessage(IN).hasText(firstUserHelloText).snapshot();

        String firstUserText = "I need your support";
        kiteChat.sendMessage(firstUserText);
        telegramChat.lastMessage(IN).hasText(firstUserText);

        String secondUserText = "Hello, I'm Second User";
        secondKiteChat.sendMessage(secondUserText);
        telegramChat.lastMessage(IN).hasText(secondUserText);

        String hostText = "Hello, First User. How can I help you?";
        telegramChat.replyMessage(message, hostText);
        kiteChat.lastMessage(IN).hasText(hostText);
      }
    }
  }
}

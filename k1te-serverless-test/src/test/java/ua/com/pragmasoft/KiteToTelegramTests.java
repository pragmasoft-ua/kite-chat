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

@Tag("kite-to-telegram")
class KiteToTelegramTests extends BaseTest {

  @BeforeAll
  static void createChannel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
  }

  @Test
  @DisplayName("User sends a text message")
  void user_sends_text_message_to_host() {
    sendTextAndVerify(kiteChat, hostChat, "Hello!");
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
    sendFileAndVerify(kiteChat, hostChat, buildPath(type));
  }

  @ParameterizedTest(name = "User sends an unsupported file with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"docx", "txt", "csv", "json"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_files_to_host(String type) {
    String uploadedFileName = kiteChat.uploadFile(buildPath(type));
    hostChat.lastMessage(IN).hasFile(uploadedFileName.replaceAll("\\..\\w+", ".zip"));
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
    sendPhotoAndVerify(kiteChat, hostChat, buildPath(type));
  }

  @ParameterizedTest(name = "User sends an unsupported image with {argumentsWithNames} to the Host")
  @ValueSource(strings = {"bmp", "tiff"})
  @DisplayName("User sends unsupported files to Host")
  void user_sends_unsupported_photos_to_host(String type) {
    kiteChat.uploadPhoto(Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type));
    hostChat.lastMessage(IN).hasFile(BASE_FILE_NAME + "zip");
  }

  @Test
  @DisplayName("User edits a sent message")
  void user_edits_sent_message() {
    ChatMessage message = sendTextAndVerify(kiteChat, hostChat, "Hello!, I'm Alev").snapshot();
    sendTextAndVerify(kiteChat, hostChat, "I need your help");

    kiteChat.editMessage(message, "Hello!, I'm Alex!");
    // TODO: 03.01.2024 Verify changed message in Telegram
  }

  @Test
  @DisplayName("User deletes a sent text message")
  void user_deletes_sent_text_message() {
    ChatMessage message = sendTextAndVerify(kiteChat, hostChat, "Hello! I need help").snapshot();
    sendTextAndVerify(kiteChat, hostChat, "Are you here?");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent file message")
  void user_deletes_sent_file_message() {
    sendTextAndVerify(kiteChat, hostChat, "Hello! I need help");
    ChatMessage message = sendFileAndVerify(kiteChat, hostChat, buildPath("pdf")).snapshot();
    sendTextAndVerify(kiteChat, hostChat, "Did you receive file?");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User deletes a sent photo message")
  void user_deletes_sent_photo_message() {
    ChatMessage message = sendPhotoAndVerify(kiteChat, hostChat, buildPath("jpg")).snapshot();
    sendTextAndVerify(kiteChat, hostChat, "Hello! I need help");

    kiteChat.deleteMessage(message);
    // TODO: 03.01.2024 Verify message is deleted in Telegram
  }

  @Test
  @DisplayName("User reconnects to the chat and recovers chat history")
  void user_reconnects_and_recover_history() {
    String firstText = "first";
    ChatMessage firstMessage = sendTextAndVerify(kiteChat, hostChat, firstText).snapshot();

    String secondText = "second";
    sendTextAndVerify(hostChat, kiteChat, secondText);
    KiteChatMessage secondMessage = kiteChat.lastMessage(IN).hasText(secondText).snapshot();

    sendPhotoAndVerify(hostChat, kiteChat, buildPath("png"));
    KiteChatMessage photoMessage = kiteChat.lastMessage(IN).isPhoto().snapshot();

    ChatMessage fileMessage = sendFileAndVerify(kiteChat, hostChat, buildPath("zip")).snapshot();

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
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User");
    sendTextAndVerify(hostChat, kiteChat, "Hello! I'm your Host");
  }

  @ParameterizedTest(name = "Host sends a file with {argumentsWithNames} to the User")
  @ValueSource(strings = {"pdf", "zip", "docx", "txt", "csv", "json"})
  @DisplayName("Host sends files to User")
  void host_sends_file_to_user(String type) {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User, " + type);
    sendFileAndVerify(hostChat, kiteChat, buildPath(type));
  }

  @ParameterizedTest(name = "Host sends an image with {argumentsWithNames} to the User")
  @ValueSource(strings = {"jpg", "png", "webp", "bmp"}) // gif is sent as a mp4 file
  @DisplayName("Host sends images to User")
  void host_sends_photo_to_user(String type) {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User, " + type);
    sendPhotoAndVerify(hostChat, kiteChat, buildPath(type));
  }

  @Test
  @DisplayName("Host replies to User's message")
  void host_replies_to_user() {
    String userText = "Hello, I'm User";
    sendTextAndVerify(kiteChat, hostChat, userText);
    ChatMessage message = hostChat.lastMessage(IN).hasText(userText).snapshot();

    String hostText = "Hello! I'm Host";
    hostChat.replyMessage(message, hostText);
    kiteChat.lastMessage(IN).hasText(hostText);
  }

  @Test
  @DisplayName("Host edits a sent message")
  void host_edits_sent_message() {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User");
    ChatMessage message = sendTextAndVerify(hostChat, kiteChat, "Hello! I'm Hos").snapshot();
    sendTextAndVerify(hostChat, kiteChat, "How can I help you?");

    String correctHostText = "Hello! I'm Host!";
    hostChat.editMessage(message, correctHostText);
    // TODO: 02.01.2024 check updated message in kite chat
  }

  @Test
  @DisplayName("Host deletes a sent text message")
  void host_deletes_sent_text_message() {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User");
    ChatMessage message = sendTextAndVerify(hostChat, kiteChat, "Hello! I'm Hos").snapshot();
    sendTextAndVerify(hostChat, kiteChat, "How can I help you?");

    hostChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent file message")
  void host_deletes_sent_file_message() {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User");
    ChatMessage message = sendFileAndVerify(hostChat, kiteChat, buildPath("zip")).snapshot();
    sendTextAndVerify(hostChat, kiteChat, "How can I help you?");

    hostChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Host deletes a sent photo message")
  void host_deletes_sent_photo_message() {
    sendTextAndVerify(kiteChat, hostChat, "Hello, I'm User");
    ChatMessage message = sendPhotoAndVerify(hostChat, kiteChat, buildPath("png")).snapshot();
    sendTextAndVerify(hostChat, kiteChat, "How can I help you?");

    hostChat.deleteMessage(message);
    // TODO: 03.01.2024 Verified message is deleted in Kite
  }

  @Test
  @DisplayName("Emulate chatting between User and host")
  void emulate_chatting() {
    sendTextAndVerify(kiteChat, hostChat, "Hello!");
    sendTextAndVerify(hostChat, kiteChat, "Hi!");
    sendTextAndVerify(hostChat, kiteChat, "How can I help you?");
    sendTextAndVerify(kiteChat, hostChat, "I don't understand. Here is a screenshot");
    sendPhotoAndVerify(kiteChat, hostChat, buildPath("png"));
    sendTextAndVerify(hostChat, kiteChat, "Here is a pdf instruction how to solve this problem");
    sendFileAndVerify(hostChat, kiteChat, buildPath("pdf"));
    sendTextAndVerify(kiteChat, hostChat, "Thank you, It helped");
    sendTextAndVerify(hostChat, kiteChat, "You are welcome");
  }

  @AfterEach
  void waiter() {
    kiteChat.waitFor(500);
    hostChat.waitFor(500);
  }

  @AfterAll
  static void dropChannel() {
    sendTextAndVerifyResponse(hostChat, DROP, DROP_RESPONSE);
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
        sendTextAndVerify(kiteChat, hostChat, firstUserHelloText);
        ChatMessage message = hostChat.lastMessage(IN).hasText(firstUserHelloText).snapshot();

        sendTextAndVerify(kiteChat, hostChat, "I need your support");

        sendTextAndVerify(secondKiteChat, hostChat, "Hello, I'm Second User");

        String hostText = "Hello, First User. How can I help you?";
        hostChat.replyMessage(message, hostText);
        kiteChat.lastMessage(IN).hasText(hostText);
      }
    }
  }
}

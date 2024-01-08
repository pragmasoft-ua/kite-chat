/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("telegram-to-telegram")
class TelegramToTelegramTests extends BaseTest {

  @BeforeAll
  static void initChannel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
    sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);
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
    sendFileAndVerify(memberChat, hostChat, buildPath(type));
  }

  @ParameterizedTest(name = "Member sends a photo with {argumentsWithNames} to the Host")
  @DisplayName("Member sends photos to the Host")
  @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
  void member_sends_photos_to_host(String type) {
    sendPhotoAndVerify(memberChat, hostChat, buildPath(type));
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
    sendFileAndVerify(hostChat, memberChat, buildPath(type));
  }

  @ParameterizedTest(name = "Host sends a photo with {argumentsWithNames} to the Member")
  @DisplayName("Host sends photos to the member")
  @ValueSource(strings = {"jpg", "bmp", "webp", "gif", "png"}) // tiff is not supported
  void host_sends_photos_to_member(String type) {
    sendPhotoAndVerify(hostChat, memberChat, buildPath(type));
  }

  @Test
  @DisplayName("Emulate chatting between Host and Member")
  void emulate_chatting() {
    sendTextAndVerify(memberChat, hostChat, "Hello!");
    sendTextAndVerify(hostChat, memberChat, "Hi!");
    sendTextAndVerify(hostChat, memberChat, "How can I help you?");
    sendTextAndVerify(memberChat, hostChat, "I don't understand. Here is a screenshot");
    sendPhotoAndVerify(memberChat, hostChat, buildPath("png"));
    sendTextAndVerify(hostChat, memberChat, "Here is a pdf instruction how to solve this problem");
    sendFileAndVerify(hostChat, memberChat, buildPath("pdf"));
    sendTextAndVerify(memberChat, hostChat, "Thank you, It helped");
    sendTextAndVerify(hostChat, memberChat, "You are welcome");
  }

  @AfterAll
  static void dropChannel() {
    sendTextAndVerifyResponse(memberChat, LEAVE, LEAVE_RESPONSE);
    sendTextAndVerifyResponse(hostChat, DROP, DROP_RESPONSE);
  }
}

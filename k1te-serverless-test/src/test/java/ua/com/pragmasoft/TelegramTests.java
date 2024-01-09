/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.ChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;

@Tag("telegram")
class TelegramTests extends BaseTest {

  @Test
  @DisplayName("User checks /info without being in a channel")
  void anonymous_info() {
    sendTextAndVerifyResponse(hostChat, INFO, ANONYMOUS_INFO_RESPONSE);
  }

  @Test
  @DisplayName("User creates a channel via /host")
  void host_new_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
  }

  @Test
  @DisplayName("User attempts to /host a channel with an existing name")
  void host_existing_channel() {
    String channelName = TELEGRAM_CHANNEL_NAME + "_test";
    String expectedHostResponse = "Created channel " + channelName;

    sendTextAndVerifyResponse(memberChat, "/host " + channelName, expectedHostResponse);

    sendTextAndVerifyResponse(hostChat, "/host " + channelName, HOST_SAME_CHANNEL_RESPONSE);
  }

  @Test
  @DisplayName("Host attempts to /host a second channel")
  void host_second_channel() {
    String channelName = TELEGRAM_CHANNEL_NAME + "_test321";
    String hostCommand = "/host " + channelName;

    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);

    sendTextAndVerifyResponse(hostChat, hostCommand, HOST_SECOND_CHANNEL_RESPONSE);
  }

  @Test
  @DisplayName("Host checks /info after creating a channel")
  @Disabled("/info with k1te_test currently doesn't work")
  void host_info() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
    sendTextAndVerifyResponse(hostChat, INFO, HOST_INFO_RESPONSE);
  }

  @Test
  @DisplayName("Member /leave a channel")
  void member_leave_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
    sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);

    sendTextAndVerifyResponse(memberChat, LEAVE, LEAVE_RESPONSE);
  }

  @Test
  @DisplayName("Host attempts to /leave their own channel")
  void host_try_leave_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);

    sendTextAndVerifyResponse(hostChat, LEAVE, HOST_LEAVE_RESPONSE);
  }

  @Test
  @DisplayName("Host /drop a channel")
  void host_delete_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);

    sendTextAndVerifyResponse(hostChat, DROP, DROP_RESPONSE);
  }

  @Test
  @DisplayName("Member attempts to /drop a channel they are not the host of")
  void member_try_delete_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
    sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);

    sendTextAndVerifyResponse(memberChat, DROP, MEMBER_DROP_RESPONSE);
  }

  @Test
  @DisplayName("Member use /help")
  void help_command() {
    sendTextAndVerifyResponse(hostChat, HELP, HELP_RESPONSE);
  }

  @Test
  @DisplayName("Member /join to a channel")
  void user_join_channel() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);

    sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);
  }

  @Test
  @DisplayName("User from client joins channel in Telegram as an existed Member")
  void user_change_connection_to_telegram() {
    sendTextAndVerifyResponse(hostChat, HOST, HOST_RESPONSE);
  }

  @AfterEach
  void dropChannels() {
    this.sendTextAndWaitForResponseAppear(memberChat, LEAVE);
    this.sendTextAndWaitForResponseAppear(memberChat, DROP);

    this.sendTextAndWaitForResponseAppear(hostChat, DROP);
  }

  private Long lastIncomingMessageId(TelegramChatPage chat) {
    return Long.parseLong(
        chat.lastMessage(ChatPage.MessageType.IN).locator().getAttribute("data-mid"));
  }

  private void sendTextAndWaitForResponseAppear(TelegramChatPage chat, String text) {
    chat.getPage().bringToFront();
    long previousResponseId = this.lastIncomingMessageId(chat);
    chat.sendMessage(text);
    chat.getPage()
        .waitForCondition(
            () -> previousResponseId < this.lastIncomingMessageId(chat),
            new Page.WaitForConditionOptions().setTimeout(6000));
  }
}

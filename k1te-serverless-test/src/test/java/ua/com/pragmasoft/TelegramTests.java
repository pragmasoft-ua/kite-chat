/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;


@Tag("telegram")
class TelegramTests extends BaseTest {
    private static final String HOST = "/host " + TELEGRAM_CHANNEL_NAME;
    private static final String DROP = "/drop";
    private static final String LEAVE = "/leave";
    private static final String JOIN = "/join " + TELEGRAM_CHANNEL_NAME;
    private static final String INFO = "/info";
    private static final String HELP = "/help";

    private static final String HOST_RESPONSE = "Created channel " + TELEGRAM_CHANNEL_NAME;
    private static final String ANONYMOUS_INFO_RESPONSE =
        "You don't have any channels at the moment";
    private static final String HOST_SAME_CHANNEL_RESPONSE = "Channel name is already taken";
    private static final String HOST_SECOND_CHANNEL_RESPONSE =
        "You cannot host more than one channel";
    private static final String HOST_INFO_RESPONSE =
        "You are a Host of the " + TELEGRAM_CHANNEL_NAME;
    private static final String JOIN_RESPONSE = "You joined channel " + TELEGRAM_CHANNEL_NAME;
    private static final String LEAVE_RESPONSE = "You left channel " + TELEGRAM_CHANNEL_NAME;
    private static final String HOST_LEAVE_RESPONSE = "Host member cannot leave channel";
    private static final String DROP_RESPONSE = "You dropped channel " + TELEGRAM_CHANNEL_NAME;
    private static final String MEMBER_DROP_RESPONSE = "Only host member can drop its channel";
    private static final String HELP_RESPONSE =
        "This bot allows to set up support channel in the current chat";

    private static Playwright playwright;
    private static Browser browser;
    private static BrowserContext telegramContext;
    private static TelegramChatPage telegramChat;
    private static TelegramChatPage memberChat;

    @BeforeAll
    static void init() {
        playwright = Playwright.create();
        browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));

        telegramContext =
            browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));
        telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);

        telegramChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_HOST_CHAT_TITLE);
        memberChat = new TelegramClientPage(telegramContext.newPage())
            .openChat(TELEGRAM_MEMBER_CHAT_TITLE);
    }

    @Test
    @DisplayName("User checks /info without being in a channel")
    void anonymous_info() {
        sendTextAndVerifyResponse(telegramChat, INFO,
            ANONYMOUS_INFO_RESPONSE);
    }

    @Test
    @DisplayName("User creates a channel via /host")
    void host_new_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);
    }

    @Test
    @DisplayName("User attempts to /host a channel with an existing name")
    void host_existing_channel() {
        String channelName = "test-k1te-channel";
        String expectedHostResponse = "Created channel " + channelName;

        sendTextAndVerifyResponse(memberChat, "/host " + channelName,
            expectedHostResponse);

        sendTextAndVerifyResponse(telegramChat, "/host " + channelName,
            HOST_SAME_CHANNEL_RESPONSE);
    }

    @Test
    @DisplayName("Host attempts to /host a second channel")
    void host_second_channel() {
        String channelName = "test-k1te-channel";
        String hostCommand = "/host " + channelName;

        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);

        sendTextAndVerifyResponse(telegramChat, hostCommand,
            HOST_SECOND_CHANNEL_RESPONSE);
    }

    @Test
    @DisplayName("Host checks /info after creating a channel")
    @Disabled("/info with k1te_test currently doesn't work")
    void host_info() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);
        sendTextAndVerifyResponse(telegramChat, INFO,
            HOST_INFO_RESPONSE);
    }

    @Test
    @DisplayName("Member /leave a channel")
    void member_leave_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);
        sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);

        sendTextAndVerifyResponse(memberChat, LEAVE, LEAVE_RESPONSE);
    }

    @Test
    @DisplayName("Host attempts to /leave their own channel")
    void host_try_leave_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);

        sendTextAndVerifyResponse(telegramChat, LEAVE, HOST_LEAVE_RESPONSE);
    }

    @Test
    @DisplayName("Host /drop a channel")
    void host_delete_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);

        sendTextAndVerifyResponse(telegramChat, DROP, DROP_RESPONSE);
    }

    @Test
    @DisplayName("Member attempts to /drop a channel they are not the host of")
    void member_try_delete_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);
        sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);

        sendTextAndVerifyResponse(memberChat, DROP, MEMBER_DROP_RESPONSE);
    }

    @Test
    @DisplayName("Member use /help")
    void help_command() {
        sendTextAndVerifyResponse(telegramChat, HELP, HELP_RESPONSE);
    }

    @Test
    @DisplayName("Member /join to a channel")
    void user_join_channel() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);

        sendTextAndVerifyResponse(memberChat, JOIN, JOIN_RESPONSE);
    }

    @Test
    @DisplayName("User from client joins channel in Telegram as an existed Member")
    void user_change_connection_to_telegram() {
        sendTextAndVerifyResponse(telegramChat, HOST, HOST_RESPONSE);
    }

    @AfterEach
    void dropChannels() {
        memberChat.getPage().bringToFront();
        memberChat.sendMessage("/leave");
        memberChat.waitFor(500);
        memberChat.sendMessage("/drop");
        memberChat.waitFor(500);

        telegramChat.getPage().bringToFront();
        telegramChat.sendMessage("/drop");
        telegramChat.waitFor(500);
    }

    @AfterAll
    static void close() {
        telegramContext.close();

        browser.close();
        playwright.close();
    }

}

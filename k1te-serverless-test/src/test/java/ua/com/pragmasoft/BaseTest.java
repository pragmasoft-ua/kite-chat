/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;
import static ua.com.pragmasoft.chat.ChatPage.MessageType.OUT;

import com.microsoft.playwright.*;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.ExtendWith;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.ChatPage;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

/**
 * Base test class for end-to-end testing of the Telegram and Kite chat integration. Manages setup
 * and teardown of necessary resources and provides utility methods for testing.
 */
@ExtendWith(BaseTest.class)
class BaseTest extends BeforeAllCallbackExtension {
  private static final String TELEGRAM_BOT_NAME = System.getProperty("bot.name", "k1techatbot");
  protected static final String TELEGRAM_CHANNEL_NAME =
      System.getProperty("channel", "k1te_chat_test");
  protected static final String KITE_CHAT_URL_WITH_CHANNEL =
      System.getProperty("kite.url", "https://www.k1te.chat/test") + "?c=" + TELEGRAM_CHANNEL_NAME;
  protected static final boolean HEADLESS = System.getProperty("headless") == null;

  protected static final String HOST = "/host " + TELEGRAM_CHANNEL_NAME;
  protected static final String DROP = "/drop";
  protected static final String LEAVE = "/leave";
  protected static final String JOIN = "/join " + TELEGRAM_CHANNEL_NAME;
  protected static final String INFO = "/info";
  protected static final String HELP = "/help";

  protected static final String HOST_RESPONSE = "Created channel " + TELEGRAM_CHANNEL_NAME;
  protected static final String ANONYMOUS_INFO_RESPONSE =
      "You don't have any channels at the moment";
  protected static final String HOST_SAME_CHANNEL_RESPONSE = "Channel name is already taken";
  protected static final String HOST_SECOND_CHANNEL_RESPONSE =
      "You cannot host more than one channel";
  protected static final String HOST_INFO_RESPONSE =
      "You are a Host of the " + TELEGRAM_CHANNEL_NAME;
  protected static final String JOIN_RESPONSE = "You joined channel " + TELEGRAM_CHANNEL_NAME;
  protected static final String LEAVE_RESPONSE = "You left channel " + TELEGRAM_CHANNEL_NAME;
  protected static final String HOST_LEAVE_RESPONSE = "Host member cannot leave channel";
  protected static final String DROP_RESPONSE = "You dropped channel " + TELEGRAM_CHANNEL_NAME;
  protected static final String MEMBER_DROP_RESPONSE = "Only host member can drop its channel";
  protected static final String HELP_RESPONSE =
      "This bot allows to set up support channel in the current chat";

  protected static final String TELEGRAM_HOST_CHAT_TITLE = "kite-host-chat";
  protected static final String TELEGRAM_MEMBER_CHAT_TITLE = "kite-member-chat";
  protected static final double DEFAULT_TIMEOUT = 6000;
  protected static final Path STORAGE_STATE_PATH = Path.of("auth.json");
  protected static final String BASE_RESOURCE_PATH = "src/test/resources";
  protected static final String BASE_FILE_NAME = "sample.";

  private static Playwright playwright;
  protected static Browser browser;
  protected static BrowserContext telegramContext;
  protected static BrowserContext kiteContext;
  private static TelegramClientPage hostClient;
  private static TelegramClientPage memberClient;
  protected static TelegramChatPage hostChat;
  protected static TelegramChatPage memberChat;
  protected static KiteChatPage kiteChat;

  /**
   * Creates two Telegram groups and adds the Bot to them. This method is invoked only once before
   * all tests start.
   *
   * @see BeforeAllCallbackExtension
   */
  @Override
  void setup() {
    BrowserType.LaunchOptions browserOptions =
        new BrowserType.LaunchOptions().setHeadless(HEADLESS);
    Browser.NewContextOptions contextOptions =
        new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH);

    playwright = Playwright.create();
    browser = playwright.chromium().launch(browserOptions);
    telegramContext = browser.newContext(contextOptions);
    telegramContext.setDefaultTimeout(DEFAULT_TIMEOUT);
    kiteContext = browser.newContext();
    kiteContext.setDefaultTimeout(DEFAULT_TIMEOUT);

    hostClient = new TelegramClientPage(telegramContext.newPage());
    hostClient.createGroupWithBot(TELEGRAM_HOST_CHAT_TITLE, TELEGRAM_BOT_NAME);
    hostClient.openChatConfig(TELEGRAM_HOST_CHAT_TITLE).makeAdmin(TELEGRAM_BOT_NAME);
    hostClient.waitFor(500); // Wait chat become available
    hostChat = hostClient.openChat(TELEGRAM_HOST_CHAT_TITLE);

    memberClient = new TelegramClientPage(telegramContext.newPage());
    memberClient.createGroupWithBot(TELEGRAM_MEMBER_CHAT_TITLE, TELEGRAM_BOT_NAME);
    memberClient.openChatConfig(TELEGRAM_MEMBER_CHAT_TITLE).makeAdmin(TELEGRAM_BOT_NAME);
    memberClient.waitFor(500); // Wait chat become available
    memberChat = memberClient.openChat(TELEGRAM_MEMBER_CHAT_TITLE);

    kiteChat = KiteChatPage.of(kiteContext.newPage(), KITE_CHAT_URL_WITH_CHANNEL);
  }

  /**
   * Deletes created groups in {@code setup()} method.
   *
   * <p>This method is executed after all test methods and ensures that created groups are deleted,
   * preventing interference with subsequent test executions.
   *
   * @see BeforeAllCallbackExtension
   */
  @Override
  void teardown() {
    hostClient.getPage().bringToFront();
    hostClient.deleteChat(TELEGRAM_HOST_CHAT_TITLE);

    memberClient.getPage().bringToFront();
    memberClient.deleteChat(TELEGRAM_MEMBER_CHAT_TITLE);

    telegramContext.close();
    kiteContext.close();
    playwright.close();
  }

  /**
   * Sends a text message from one ChatPage to another, verifying its delivery.
   *
   * <p>This method sends a text message from the 'from' chat to the 'to' chat, ensuring that the
   * message is delivered and seen in the 'to' chat box.
   *
   * @param from The source chat.
   * @param to The destination chat.
   * @param text The text message to send.
   * @return ChatMessage instance that points at a sent message in 'from' chat.
   */
  public static ChatMessage sendTextAndVerify(ChatPage from, ChatPage to, String text) {
    from.getPage().bringToFront();
    from.sendMessage(text);
    ChatMessage message = from.lastMessage(OUT);
    to.getPage().bringToFront();
    to.lastMessage(IN).hasText(text);
    return message;
  }

  /**
   * Sends a file message from one ChatPage to another, verifying its delivery.
   *
   * <p>This method sends a file message from the 'from' chat to the 'to' chat, ensuring that the
   * message is delivered and seen in the 'to' chat box.
   *
   * @param from The source chat.
   * @param to The destination chat.
   * @param path The path to the file to send.
   * @return ChatMessage instance that points at a sent message in 'from' chat.
   */
  public static ChatMessage sendFileAndVerify(ChatPage from, ChatPage to, Path path) {
    from.getPage().bringToFront();
    String uploadedFile = from.uploadFile(path);
    ChatMessage message = from.lastMessage(OUT);
    to.getPage().bringToFront();
    to.lastMessage(IN).hasFile(uploadedFile);
    return message;
  }

  /**
   * Sends a photo message from one ChatPage to another, verifying its delivery.
   *
   * <p>This method sends a photo message from the 'from' chat to the 'to' chat, ensuring that the
   * message is delivered and seen in the 'to' chat box.
   *
   * @param from The source chat.
   * @param to The destination chat.
   * @param path The path to the photo to send.
   * @return ChatMessage instance that points at a sent message in 'from' chat.
   */
  public static ChatMessage sendPhotoAndVerify(ChatPage from, ChatPage to, Path path) {
    from.getPage().bringToFront();
    from.uploadPhoto(path);
    ChatMessage message = from.lastMessage(OUT);
    to.waitFor(500); // We can not verify specific photo as we do with files by their names
    // and sometimes last sent photo can be estimated
    // as a new one that may corrupt test, so wee need to wait to prevent such behaviour
    to.getPage().bringToFront();
    to.lastMessage(IN).isPhoto();
    return message;
  }

  /**
   * Sends a text message to the chat and waits and verifies the received response.
   *
   * <p>This method sends a text message to the chat and verifies the response received in the same
   * chat.
   *
   * @param chat The target chat.
   * @param text The text message to send.
   * @param expected The expected response.
   */
  public static void sendTextAndVerifyResponse(ChatPage chat, String text, String expected) {
    chat.getPage().bringToFront();
    chat.sendMessage(text);
    chat.lastMessage(IN).hasText(expected);
  }

  public static Path buildPath(String type) {
    return Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
  }
}

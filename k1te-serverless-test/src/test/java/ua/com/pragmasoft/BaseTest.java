/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.IN;
import static ua.com.pragmasoft.chat.ChatPage.MessageType.OUT;

import com.microsoft.playwright.*;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.ChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

class BaseTest {
  private static final String TELEGRAM_BOT_NAME = System.getProperty("bot.name", "k1techatbot");
  protected static final String TELEGRAM_CHANNEL_NAME = System.getProperty("channel","k1te_test");
  protected static final String KITE_CHAT_URL_WITH_CHANNEL =
      System.getProperty("kite.url", "https://www.k1te.chat/test") + "?c=" + TELEGRAM_CHANNEL_NAME;

  protected static final Path STORAGE_STATE_PATH = Path.of("auth.json");
  protected static final String TELEGRAM_HOST_CHAT_TITLE = "kite-host-chat";
  protected static final String TELEGRAM_MEMBER_CHAT_TITLE = "kite-member-chat";
  protected static final double DEFAULT_TIMEOUT = 6000;
  protected static final String BASE_RESOURCE_PATH = "src/test/resources";
  protected static final String BASE_FILE_NAME = "sample.";
  protected static final boolean HEADLESS = true;
  private static boolean successFlag = false;


  /**
   * Creates two Telegram groups and adds the Bot to them.
   *
   * <p>If an exception is thrown, the AfterAll method will not delete chats.
   * It's useful if you have a chat with the same name that is going to be created,
   * and to avoid deleting it, the initialization fails and set successFlag to false.
   */
  @BeforeAll
  static void createGroups() {
    Playwright playwright = Playwright.create();
    Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
    BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));

    try (playwright;
        browser;
        context) {
      Page page = context.newPage();

      TelegramClientPage telegramClientPage = new TelegramClientPage(page);
      telegramClientPage.createGroupWithBot(TELEGRAM_HOST_CHAT_TITLE, TELEGRAM_BOT_NAME);
      telegramClientPage.openChatConfig(TELEGRAM_HOST_CHAT_TITLE).makeAdmin(TELEGRAM_BOT_NAME);

      telegramClientPage.createGroupWithBot(TELEGRAM_MEMBER_CHAT_TITLE, TELEGRAM_BOT_NAME);
      telegramClientPage.openChatConfig(TELEGRAM_MEMBER_CHAT_TITLE).makeAdmin(TELEGRAM_BOT_NAME);

      successFlag = true;
    }
  }

  /**
   * Deletes created groups from BeforeAll step.
   *
   * <p>This method is executed after all test methods and ensures that created groups
   * are deleted, preventing interference with subsequent test executions.
   *
   * @throws IllegalStateException If the initialization phase has not finished successfully.
   */
  @AfterAll
  static void cleanGroups() {
    if (!successFlag)
      throw new IllegalStateException("Init phase has not finished successfully");

    Playwright playwright = Playwright.create();
    Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(HEADLESS));
    BrowserContext context =
        browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));

    try (playwright;
        browser;
        context) {
      Page page = context.newPage();
      TelegramClientPage telegramClientPage = new TelegramClientPage(page);
      telegramClientPage.deleteChat(TELEGRAM_HOST_CHAT_TITLE);
      telegramClientPage.deleteChat(TELEGRAM_MEMBER_CHAT_TITLE);
    }
  }

  /**
   * Sends a text message from one ChatPage to another, verifying its delivery.
   *
   * <p>This method sends a text message from the 'from' chat to the 'to' chat,
   * ensuring that the message is delivered and seen in the 'to' chat box.
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
   * <p>This method sends a file message from the 'from' chat to the 'to' chat,
   * ensuring that the message is delivered and seen in the 'to' chat box.
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
   * <p>This method sends a photo message from the 'from' chat to the 'to' chat,
   * ensuring that the message is delivered and seen in the 'to' chat box.
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
   * <p>This method sends a text message to the chat and verifies the response received
   * in the same chat.
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

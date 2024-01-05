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
  protected static final boolean HEADLESS = false;

  private static boolean successFlag = false;
  private static boolean initPhaseInvoked = false;

  @BeforeAll
  static void createGroups() {
    if (initPhaseInvoked)
      return; //If groups already created from the previous test suite, skip their creation

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
      initPhaseInvoked = true;
    }
  }

  @AfterAll
  static void cleanGroups() {
    if (initPhaseInvoked)
      return; //If groups already created from the previous test suite, skip their creation
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

  // TODO: 05.01.2024 doc
  public static ChatMessage sendTextAndVerify(ChatPage from, ChatPage to, String text) {
    from.getPage().bringToFront();
    from.sendMessage(text);
    ChatMessage message = from.lastMessage(OUT);
    to.getPage().bringToFront();
    to.lastMessage(IN).hasText(text);
    return message;
  }

  public static ChatMessage sendFileAndVerify(ChatPage from, ChatPage to, Path path) {
    from.getPage().bringToFront();
    String uploadedFile = from.uploadFile(path);
    ChatMessage message = from.lastMessage(OUT);
    to.getPage().bringToFront();
    to.lastMessage(IN).hasFile(uploadedFile);
    return message;
  }

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

  public static void sendTextAndVerifyResponse(ChatPage chat, String text, String expected) {
    chat.getPage().bringToFront();
    chat.sendMessage(text);
    chat.lastMessage(IN).hasText(expected);
  }

  public static Path buildPath(String type) {
    return Path.of(BASE_RESOURCE_PATH, BASE_FILE_NAME + type);
  }
}

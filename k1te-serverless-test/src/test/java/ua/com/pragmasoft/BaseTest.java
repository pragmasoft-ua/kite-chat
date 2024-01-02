/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;

public class BaseTest {
  private static Playwright playwright;
  private static Browser browser;
  private static BrowserContext kiteContext;
  protected static KiteChatPage kiteChat;
  private static BrowserContext telegramContext;
  protected static TelegramChatPage telegramChat;

  protected static final String BASE_PATH = "src/test/resources";

  @BeforeAll
  static void init() {
    // TODO: 27.12.2023
    //        String telegramChatTitle = System.getProperty("chat-title", "Kite.chat.new.bot");
    String telegramChatTitle = System.getProperty("chat-title", "www.k1te.chat");
    String kiteUrl = System.getProperty("kite-url", "https://www.k1te.chat/test");

    playwright = Playwright.create();
    // TODO: 27.12.2023
    browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));

    telegramContext =
        browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
    telegramChat = TelegramChatPage.of(telegramContext.newPage(), telegramChatTitle);

    kiteContext = browser.newContext();
    kiteChat = KiteChatPage.of(kiteContext.newPage(), kiteUrl);
  }

  @AfterAll
  static void close() {
    telegramContext.close();
    kiteContext.close();
    browser.close();
    playwright.close();
  }
}

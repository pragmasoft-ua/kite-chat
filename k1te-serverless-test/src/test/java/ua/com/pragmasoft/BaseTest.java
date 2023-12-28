/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import ua.com.pragmasoft.telegram.TelegramChat;

public class BaseTest {
  private static Playwright playwright;
  private static Browser browser;
  private static BrowserContext context;
  protected static TelegramChat telegramChat;
  protected static Page page;

  @BeforeAll
  static void init() {
    // TODO: 27.12.2023
    String chatTitle = System.getProperty("chat-title", "Kite.chat.new.bot");
    playwright = Playwright.create();
    // TODO: 27.12.2023
    browser = playwright.chromium().launch();
    context =
        browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
    page = context.newPage();
    telegramChat = TelegramChat.of(page, chatTitle);
  }

  @AfterAll
  static void close() {
    context.close();
    browser.close();
    playwright.close();
  }
}

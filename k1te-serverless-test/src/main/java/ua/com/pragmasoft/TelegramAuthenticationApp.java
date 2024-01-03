/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import java.nio.file.Path;

/**
 * This application is designed to facilitate the authentication process for Telegram Web and retrieve the 'auth.json' file.
 * The 'auth.json' file stores essential cookies and local storage data necessary for conducting tests in the Kite Chat application.
 * To run this application, execute it via the Maven exec plugin while located in the 'kite-chat' directory.
 *
 * <p>You can use the following Maven command to run the application:
 * <pre>{@code
 * ./mvnw -pl k1te-serverless-test compile exec:java -Dexec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp
 * }</pre>
 *
 * <p>This command launches the Chromium browser, navigates to the Telegram Web page, and prompts you to log in.
 * Once the login is successful, the browser closes, and the 'auth.json' file is generated in the 'k1te-serverless-test' directory.
 *
 * <p>You can also specify the login timeout as a system property. The default timeout is 60 seconds.
 * To set a custom timeout, include the '-Dtimeout' parameter in the command. For example:
 * <pre>{@code
 * ./mvnw -pl k1te-serverless-test compile exec:java -Dexec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp -Dtimeout=80000
 * }</pre>
 */
public class TelegramAuthenticationApp {
  private static final String TELEGRAM_WEB_URL = "https://web.telegram.org/";

  public static void main(String[] args) {
    String timeout = System.getProperty("timeout", "60000");
    Playwright playwright = Playwright.create();
    Browser browser =
        playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
    BrowserContext context = browser.newContext();

    try (playwright;
        browser;
        context;
        Page page = context.newPage()) {
      page.navigate(TELEGRAM_WEB_URL);
      page.waitForCondition(
          () -> page.locator("#page-chats").isVisible(),
          new Page.WaitForConditionOptions().setTimeout(Double.parseDouble(timeout)));
      page.waitForTimeout(1000);
      context.storageState(
          new BrowserContext.StorageStateOptions()
              .setPath(Path.of("k1te-serverless-test/auth.json")));
    }
  }
}

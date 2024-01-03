/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import java.nio.file.Path;

/**
 * This app is used to log in Telegram Web and retrieve auth.json that stores cookies and local
 * storage which are necessary for testing. You can run this app via exec plugin being in kite-chat
 * directory <br>
 * ./mvnw -pl k1te-serverless-test compile exec:java -D
 * exec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp <br>
 * It will launch Chromium browser and open Telegram Web Page, you will need to log in there, once
 * you successfully logged in, the browser will close and auth.json file will be created. <br>
 * You also can pass timeout as a system property, it's responsible for time to log in, default is
 * 60s <br>
 * ./mvnw -pl k1te-serverless-test compile exec:java -D
 * exec.mainClass=ua.com.pragmasoft.TelegramAuthenticationApp -Dtimeout=80000
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

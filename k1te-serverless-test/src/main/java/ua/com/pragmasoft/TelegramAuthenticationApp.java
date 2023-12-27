package ua.com.pragmasoft;

import com.microsoft.playwright.*;

import java.nio.file.Path;

public class TelegramAuthenticationApp {
    private static final String TELEGRAM_WEB_URL = "https://web.telegram.org/";

    public static void main(String[] args) {
        String timeout = System.getProperty("timeout", "60000");
        Playwright playwright = Playwright.create();
        Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
            .setHeadless(false));
        BrowserContext context = browser.newContext();

        try (playwright; browser; context; Page page = context.newPage()) {
            page.navigate(TELEGRAM_WEB_URL);
            page.waitForCondition(() -> page.locator("#page-chats").isVisible(),
                new Page.WaitForConditionOptions().setTimeout(Double.parseDouble(timeout)));
            page.waitForTimeout(1000);
            context.storageState(
                new BrowserContext.StorageStateOptions().setPath(
                    Path.of("k1te-serverless-test/auth.json")));
        }
    }
}

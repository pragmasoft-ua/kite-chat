/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

import java.nio.file.Path;

public class BaseTest {
    private static final String TELEGRAM_BOT_NAME = System.getProperty("bot.name", "@k1techatbot");
    protected static final String KITE_CHAT_URL =
        System.getProperty("kite.url", "https://www.k1te.chat/test");

    protected static final Path STORAGE_STATE_PATH = Path.of("auth.json");
    protected static final String CHANNEL_NAME = "k1te-test";
    protected static final String TELEGRAM_HOST_CHAT_TITLE = "kite-host-chat";
    protected static final String TELEGRAM_MEMBER_CHAT_TITLE = "kite-member-chat";
    protected static final double DEFAULT_TIMEOUT = 6000;
    protected static final String BASE_RESOURCE_PATH = "src/test/resources";
    protected static final String BASE_FILE_NAME = "sample.";
    private static boolean successFlag = false;

    @BeforeAll
    static void createGroups() {
        Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false)); // TODO: 04.01.2024  
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));

        try (playwright;
            browser;
            context) {
            Page page = context.newPage();
            TelegramClientPage telegramClientPage = new TelegramClientPage(page);
            telegramClientPage.createGroupWithBot(TELEGRAM_HOST_CHAT_TITLE,TELEGRAM_BOT_NAME);
            telegramClientPage.createGroupWithBot(TELEGRAM_MEMBER_CHAT_TITLE,TELEGRAM_BOT_NAME);
            successFlag = true;
        }
    }

    @Test
    void test(){
        System.out.println("Fisrt");
    }

    @AfterAll
    static void cleanGroups() {
        if (!successFlag)
            throw new IllegalStateException("Init phase has not finished successfully");

        Playwright playwright = Playwright.create();
        Browser browser =
            playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false)); // TODO: 04.01.2024  
        BrowserContext context = browser.newContext(new Browser.NewContextOptions().setStorageStatePath(STORAGE_STATE_PATH));

        try (playwright;
            browser;
            context) {
            Page page = context.newPage();
            TelegramClientPage telegramClientPage = new TelegramClientPage(page);
            telegramClientPage.deleteChat(TELEGRAM_HOST_CHAT_TITLE);
            telegramClientPage.deleteChat(TELEGRAM_MEMBER_CHAT_TITLE);
        }
    }
}

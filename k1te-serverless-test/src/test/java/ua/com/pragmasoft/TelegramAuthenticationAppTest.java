package ua.com.pragmasoft;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import org.junit.jupiter.api.*;

import java.nio.file.Path;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

class TelegramAuthenticationAppTest {

    private static Playwright playwright;
    private static Browser browser;

    private BrowserContext context;
    private Page page;

    @BeforeAll
    static void initPlaywright() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
        //        browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(false));
    }

    @BeforeEach
    void initContextAndPage() {
        context = browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Path.of("auth.json")));
        page = context.newPage();
        page.navigate("https://web.telegram.org");
    }

    @Test
    void test() {
        Locator leftColumn = page.locator("#column-left");
        Locator chatList = leftColumn.locator(".chatlist-top > ul.chatlist");
        Locator kiteChat =
            chatList.getByRole(AriaRole.LINK)
                .filter(new Locator.FilterOptions()
                    .setHas(page.locator("div.user-title",
                        new Page.LocatorOptions().setHasText("Kite.chat.new.bot"))));

        assertThat(kiteChat).hasCount(1);
        assertThat(kiteChat).isVisible();
        kiteChat.click();

        Locator chat = page.locator("#column-center");
        Locator input = chat.locator("div.input-message-input:not(.input-field-input-fake)");
        Locator sendButton = chat.locator("button.send");
        input.fill("hello");
        sendButton.click();
        page.waitForTimeout(500);
    }


    @AfterEach
    void closeContext() {
        context.close();
    }

    @AfterAll
    static void closePlaywright() {
        playwright.close();
    }
}


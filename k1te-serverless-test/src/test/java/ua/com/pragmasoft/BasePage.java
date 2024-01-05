/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramClientPage;

/**
 * The base class for all page classes.
 * It provides common methods and functionalities that are shared among different page classes.
 *
 * <p>The primary purpose of this class is to serve as a foundation for other page classes, offering methods
 * for waiting, clicking on elements, and accessing the underlying Playwright Page instance.
 *
 * @see TelegramChatPage
 * @see KiteChatPage
 * @see TelegramClientPage
 */
public class BasePage {
  protected final Page page;

  public BasePage(Page page) {
    this.page = page;
  }

  /**
   * Waits for the specified duration in milliseconds.
   *
   * @param timeout The duration to wait in milliseconds.
   */
  public void waitFor(double timeout) {
    this.page.waitForTimeout(timeout);
  }

  /**
   * Clicks on the specified locator and waits for the specified duration.
   *
   * @param locator The Playwright Locator to be clicked.
   * @param timeout The duration to wait after clicking in milliseconds.
   */
  public void clickAndWait(Locator locator, double timeout) {
    this.clickAndWait(locator, timeout, null);
  }

  /**
   * Clicks on the specified locator with the provided options and waits for the specified duration.
   *
   * @param locator The Playwright Locator to be clicked.
   * @param timeout The duration to wait after clicking in milliseconds.
   * @param options The ClickOptions to be applied during the click operation.
   */
  public void clickAndWait(Locator locator, double timeout, Locator.ClickOptions options) {
    locator.click(options);
    this.page.waitForTimeout(timeout);
  }

  public Page getPage() {
    return page;
  }
}

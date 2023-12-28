/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.telegram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;

public final class TelegramChat {
  private static final String TELEGRAM_WEB_URL = "https://web.telegram.org";

  private final Page page;
  private final Locator messageGroups;
  private final Locator incomingMessages;
  private final Locator outcomingMessages;
  private final Locator input;
  private final Locator sendButton;

  public static TelegramChat of(Page page, String chatTitle) {
    page.navigate(TELEGRAM_WEB_URL);
    Locator chatList = page.locator(".chatlist-top").getByRole(AriaRole.LINK);
    Locator chat =
        chatList.filter(
            new Locator.FilterOptions()
                .setHas(
                    page.locator(
                        "div.user-title", new Page.LocatorOptions().setHasText(chatTitle))));

    assertThat(chat).hasCount(1);
    chat.click();
    return new TelegramChat(page);
  }

  private TelegramChat(Page page) {
    this.page = page;
    Locator chat = page.locator("#column-center").locator("div.chat");

    this.messageGroups = chat.locator("div.bubbles").locator("div.bubbles-group");
    this.incomingMessages = this.messageGroups.locator(".bubble.is-in");
    this.outcomingMessages = this.messageGroups.locator(".bubble.is-out");

    this.input = chat.locator("div.input-message-input:not(.input-field-input-fake)");
    this.sendButton = chat.locator("button.send");
  }

  public String lastMessage() {
    return this.lastMessage(MessageType.ANY);
  }

  public String lastMessage(MessageType type) {
    String message =
        switch (type) {
          case IN -> this.incomingMessages.last().innerText();
          case OUT -> this.outcomingMessages.last().innerText();
          case ANY -> this.messageGroups.last().innerText();
        };
    return message.substring(0, message.lastIndexOf("\n"));
  }

  public String sendMessageAndWaitResponse(String text) {
    long lastIncomingMessageId =
        Long.parseLong(this.incomingMessages.last().getAttribute("data-mid"));

    this.input.fill(text);
    this.sendButton.click();
    this.page.waitForTimeout(500);
    assertThat(this.outcomingMessages.last()).hasText(Pattern.compile(text + ".\\w+"));

    int overallTimeout = 0;
    while (lastIncomingMessageId
        == Long.parseLong(this.incomingMessages.last().getAttribute("data-mid"))) {
      if (overallTimeout > 20_000)
        throw new IllegalStateException("Timeout for response is exceeded");
      this.page.waitForTimeout(100);
      overallTimeout += 100;
    }

    return this.lastMessage(MessageType.IN);
  }

  public enum MessageType {
    IN,
    OUT,
    ANY
  }
}

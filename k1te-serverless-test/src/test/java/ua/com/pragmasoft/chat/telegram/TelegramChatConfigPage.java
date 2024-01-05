/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat.telegram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import java.util.regex.Pattern;
import ua.com.pragmasoft.BasePage;

/**
 * Represents a configuration page for a Telegram group chat, providing methods to modify group
 * settings. This class is specifically designed for handling configuration changes in group-type
 * chats.
 *
 * <p>Instances of TelegramChatConfigPage are utilized to perform actions related to group
 * configuration, such as promoting a member to an administrator, opening specific configuration
 * sections, and navigating through the group configuration interface.
 *
 * <p><b>NOTE:</b> This class is intended to be used only for group-type chats.
 *
 * @see TelegramChatPage
 */
public class TelegramChatConfigPage extends BasePage {
  private final Locator sections;
  private final Locator membersList;
  private final Locator backButton;

  private TelegramChatConfigPage(Page page) {
    super(page);
    Locator rightColumn = page.locator("#column-right");
    this.sections = rightColumn.locator(".sidebar-left-section-content >> .row");
    this.membersList = rightColumn.locator(".chatlist-container >> ul.chatlist >> a");
    this.backButton = rightColumn.locator(".sidebar-header").getByRole(AriaRole.BUTTON).filter();
  }

  /**
   * Creates an instance of TelegramChatConfigPage associated with the provided Playwright Page and
   * navigates to the configuration settings for the specified group chat. This method is
   * specifically designed for group-type chats and should be used accordingly.
   *
   * @param page The Playwright Page instance associated with the Telegram web application.
   * @param chatTitle The title of the group chat for which configuration is to be modified.
   * @return A new instance of TelegramChatConfigPage associated with the specified group chat.
   */
  public static TelegramChatConfigPage of(Page page, String chatTitle) {
    Locator chatInfo = page.locator("#column-center >> .chat-info");
    Locator title = chatInfo.locator(".user-title");
    assertThat(title)
        .hasText(
            Pattern.compile(chatTitle), new LocatorAssertions.HasTextOptions().setTimeout(2000));
    chatInfo.click();
    page.waitForTimeout(500);

    Locator rightColumn = page.locator("#column-right");
    rightColumn.locator(".sidebar-header").getByRole(AriaRole.BUTTON).last().click();
    page.waitForTimeout(500);
    return new TelegramChatConfigPage(page);
  }

  /**
   * Promotes a specified member to the administrator role within the group chat.
   *
   * @param memberName The name of the member to be promoted to an administrator.
   */
  public void makeAdmin(String memberName) {
    this.openSection(ConfigSection.ADMINISTRATORS);
    Locator member = this.findMember(memberName);
    assertThat(member).hasCount(1);
    this.clickAndWait(member, 500);
    this.clickAndWait(this.backButton, 500);
  }

  private void openSection(ConfigSection section) {
    Locator item =
        this.sections.filter(
            new Locator.FilterOptions().setHasText(Pattern.compile(section.value)));
    this.clickAndWait(item, 500);
  }

  private Locator findMember(String name) {
    return this.membersList.filter(
        new Locator.FilterOptions()
            .setHas(this.page.locator(".user-title", new Page.LocatorOptions().setHasText(name))));
  }

  private enum ConfigSection {
    GROUP_TYPE("Group Type"),
    INVITE_LINKS("Invite Links"),
    REACTIONS("Reactions"),
    PERMISSIONS("Permissions"),
    TOPICS("Topics"),
    ADMINISTRATORS("Administrators"),
    MEMBERS("Members"),
    REMOVED_USERS("Removed users");

    final String value;

    ConfigSection(String value) {
      this.value = value;
    }
  }
}

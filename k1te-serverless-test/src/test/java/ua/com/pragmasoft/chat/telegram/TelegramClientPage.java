/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat.telegram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.MouseButton;
import java.util.regex.Pattern;
import ua.com.pragmasoft.BasePage;

/**
 * Represents the main Telegram page where chats are located, providing methods to manage chats,
 * create new groups, open chats, and perform actions within chats. This class is utilized for
 * interactions with the Telegram web interface.
 *
 * @see TelegramChatPage
 * @see TelegramChatConfigPage
 */
public class TelegramClientPage extends BasePage {
  private static final String TELEGRAM_WEB_URL = "https://web.telegram.org";

  private final Locator chatList;
  private final Locator createButton;
  private final Locator createChatMenuItems;
  private final Locator chatMenuItems;

  private final Locator deleteForAllMembersCheckBox;
  private final Locator deleteGroupButton;

  private final Locator membersList;
  private final Locator addPeopleInput;
  private final Locator confirmMembersSelectedButton;

  private final Locator groupNameInput;
  private final Locator confirmGroupCreationButton;

  public TelegramClientPage(Page page) {
    super(page);
    Locator mainSideSlider = page.locator(".item-main");
    this.createButton = mainSideSlider.locator("#new-menu");
    this.createChatMenuItems = this.createButton.locator(".btn-menu >> .btn-menu-item");
    this.chatList = mainSideSlider.locator(".chatlist-top >> a");
    this.chatMenuItems = page.locator(".btn-menu >> .btn-menu-item");

    Locator popup = page.locator(".popup");
    this.deleteForAllMembersCheckBox = popup.locator("label");
    this.deleteGroupButton =
        popup.getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete group"));

    Locator addMembersContainer = page.locator(".add-members-container");
    this.membersList = addMembersContainer.locator("ul.chatlist >> a");
    this.addPeopleInput = addMembersContainer.getByPlaceholder(Pattern.compile("Add people"));
    this.confirmMembersSelectedButton =
        addMembersContainer.locator(".sidebar-content").getByRole(AriaRole.BUTTON);

    Locator newGroupContainer = page.locator(".new-group-container");
    this.groupNameInput = newGroupContainer.locator(".input-field-input[contenteditable=true]");
    this.confirmGroupCreationButton =
        newGroupContainer.locator(".sidebar-content").getByRole(AriaRole.BUTTON);

    this.page.navigate(TELEGRAM_WEB_URL, new Page.NavigateOptions().setTimeout(15_000));
    this.waitFor(2000); // Waits until components become functional
  }

  /**
   * Creates an instance of {@link TelegramChatPage} and navigates to the specified chat. This
   * method is typically used to initialize a TelegramChatPage for a specific chat session.
   *
   * @param chatTitle The title of the chat to be opened.
   * @return A new instance of TelegramChatPage associated with the specified chat.
   */
  public TelegramChatPage openChat(String chatTitle) {
    Locator chat = this.findChatByTitle(this.chatList, chatTitle);
    assertThat(chat).hasCount(1, new LocatorAssertions.HasCountOptions().setTimeout(30_000));
    this.clickAndWait(chat, 500);
    return new TelegramChatPage(this.page, chatTitle);
  }

  /**
   * Creates an instance of {@link TelegramChatConfigPage} and navigates to the specified chat. This
   * method is typically used to initialize a TelegramChatConfigPage for a specific chat session.
   *
   * @param chatTitle The title of the chat to be opened.
   * @return A new instance of TelegramChatConfigPage associated with the specified chat.
   */
  public TelegramChatConfigPage openChatConfig(String chatTitle) {
    return this.openChat(chatTitle).openChatConfig();
  }

  /**
   * Creates a new group with a specified title and adds a bot to it.
   *
   * @param groupTitle The title for the new group.
   * @param botName The name of the bot to be added to the group.
   */
  public void createGroupWithBot(String groupTitle, String botName) {
    Locator chat = this.findChatByTitle(this.chatList, groupTitle);
    if (chat.count() != 0)
      throw new IllegalStateException("The chat with name " + groupTitle + " already exists");

    this.doActionOnCreateMenu(CreateMenuAction.NEW_GROUP);

    this.addPeopleInput.fill(botName);
    this.waitFor(1000);
    Locator bot = this.findChatByTitle(this.membersList, botName);
    if (bot.count() != 1) throw new IllegalStateException("There is no bot with name " + botName);

    bot.click();
    this.confirmMembersSelectedButton.click();

    this.groupNameInput.clear();
    this.groupNameInput.fill(groupTitle);
    this.clickAndWait(this.confirmGroupCreationButton, 500);
    Locator group = this.findChatByTitle(chatList, groupTitle);
    assertThat(group).hasCount(1);
    this.page.reload(); // This reload is necessary because Telegram page may become corrupted
    // and some functions can not be accessed in the future
    this.waitFor(1500);
  }

  /**
   * Deletes a chat with the specified title.
   *
   * @param title The title of the chat to be deleted.
   */
  public void deleteChat(String title) {
    Locator chat = this.findChatByTitle(chatList, title);
    if (chat.count() != 1) throw new IllegalStateException("There is no chat with title " + title);

    this.doActionOnChat(chat, ChatMenuAction.DELETE);
    this.deleteForAllMembersCheckBox.click();
    this.clickAndWait(this.deleteGroupButton, 500);
  }

  private Locator findChatByTitle(Locator list, String title) {
    return list.filter(
        new Locator.FilterOptions()
            .setHas(this.page.locator(".user-title", new Page.LocatorOptions().setHasText(title))));
  }

  private void doActionOnCreateMenu(CreateMenuAction action) {
    this.clickAndWait(this.createButton, 700); // May not be interactive
    Locator item =
        this.createChatMenuItems.filter(
            new Locator.FilterOptions().setHasText(Pattern.compile(action.value)));
    this.clickAndWait(item, 500);
  }

  private void doActionOnChat(Locator chat, ChatMenuAction action) {
    this.clickAndWait(chat, 700, new Locator.ClickOptions().setButton(MouseButton.RIGHT));
    Locator item =
        this.chatMenuItems.filter(
            new Locator.FilterOptions().setHasText(Pattern.compile(action.value)));
    this.clickAndWait(item, 500);
  }

  private enum CreateMenuAction {
    NEW_CHANNEL("New Channel"),
    NEW_GROUP("New Group"),
    NEW_PRIVATE_CHAT("New Private Chat");

    final String value;

    CreateMenuAction(String value) {
      this.value = value;
    }
  }

  private enum ChatMenuAction {
    OPEN_IN_NEW_TAB("Open in new tab"),
    MARK_AS_UNREAD("Mark as unread"),
    PIN("Pin"),
    UNMUTE("Unmute"),
    ARCHIVE("Archive"),
    DELETE("Delete");

    final String value;

    ChatMenuAction(String value) {
      this.value = value;
    }
  }
}

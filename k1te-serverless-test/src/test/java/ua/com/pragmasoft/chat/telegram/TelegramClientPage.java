package ua.com.pragmasoft.chat.telegram;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.MouseButton;
import org.junit.jupiter.api.Assertions;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public class TelegramClientPage {
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

    private final Page page;

    public TelegramClientPage(Page page) {
        this.page = page;

        Locator mainSideSlider = page.locator(".item-main");
        this.createButton = mainSideSlider.locator("#new-menu");
        this.createChatMenuItems = this.createButton.locator(".btn-menu >> .btn-menu-item");
        this.chatList = mainSideSlider.locator(".chatlist-top >> a");
        this.chatMenuItems = page.locator(".btn-menu >> .btn-menu-item");

        Locator popup = page.locator(".popup");
        this.deleteForAllMembersCheckBox = popup.locator("label");
        this.deleteGroupButton = popup.getByRole(AriaRole.BUTTON,new Locator.GetByRoleOptions().setName("Delete group"));

        Locator addMembersContainer = page.locator(".add-members-container");
        this.membersList = addMembersContainer.locator("ul.chatlist >> a");
        this.addPeopleInput = addMembersContainer.getByPlaceholder(Pattern.compile("Add people"));
        this.confirmMembersSelectedButton =
            addMembersContainer.locator(".sidebar-content").getByRole(AriaRole.BUTTON);

        Locator newGroupContainer = page.locator(".new-group-container");
        this.groupNameInput = newGroupContainer.locator(".input-field-input[contenteditable=true]");
        this.confirmGroupCreationButton =
            newGroupContainer.locator(".sidebar-content").getByRole(AriaRole.BUTTON);

        this.page.navigate(TELEGRAM_WEB_URL);
        page.waitForTimeout(2000); // Waits until components become functional
    }

    public void createGroupWithBot(String groupTitle, String botName) {
        Locator chat = this.findChatByTitle(this.chatList, groupTitle);
        Assertions.assertEquals(0, chat.count(),
            () -> "The chat with name " + groupTitle + " already exists");

        this.createButton.click();
        this.page.waitForTimeout(500);
        this.createChatMenuItems.filter(new Locator.FilterOptions()
            .setHasText(Pattern.compile(CreateMenuItem.NEW_GROUP.value))).click();

        this.addPeopleInput.fill(botName);
        this.page.waitForTimeout(500);
        Locator bot =
            this.findChatByTitle(this.membersList, botName.replace("@", ""));
        Assertions.assertEquals(1, bot.count(), () -> "There is no bot with name " + botName);
        bot.click();
        this.confirmMembersSelectedButton.click();

        this.groupNameInput.clear();
        this.groupNameInput.fill(groupTitle);
        this.confirmGroupCreationButton.click();
        this.page.waitForTimeout(500);
        Locator group = this.findChatByTitle(chatList, groupTitle);
        assertThat(group).hasCount(1);
        page.reload(); // This reload is necessary because Telegram page may become corrupted
        // and some functions can not be accessed in the future
        page.waitForTimeout(1500);
    }

    public void deleteChat(String title) {
        Locator chat = this.findChatByTitle(chatList, title);
        Assertions.assertEquals(1, chat.count(), () -> "There is no chat with title " + title);
        chat.click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
        this.page.waitForTimeout(500);
        this.chatMenuItems.filter(new Locator.FilterOptions().setHasText(Pattern.compile(ChatMenuItem.DELETE.value)))
            .click();
        this.page.waitForTimeout(500);
        this.deleteForAllMembersCheckBox.click();
        this.deleteGroupButton.click();
        this.page.waitForTimeout(500);
    }

    private Locator findChatByTitle(Locator list, String title) {
        return list.filter(new Locator.FilterOptions()
            .setHas(this.page.locator(".user-title",
                new Page.LocatorOptions().setHasText(title))));
    }

    private enum CreateMenuItem {
        NEW_CHANNEL("New Channel"),
        NEW_GROUP("New Group"),
        NEW_PRIVATE_CHAT("New Private Chat");

        final String value;

        CreateMenuItem(String value) {
            this.value = value;
        }
    }

    private enum ChatMenuItem {
        OPEN_IN_NEW_TAB("Open in new tab"),
        MARK_AS_UNREAD("Mark as unread"),
        PIN("Pin"),
        UNMUTE("Unmute"),
        ARCHIVE("Archive"),
        DELETE("Delete");

        final String value;

        ChatMenuItem(String value) {
            this.value = value;
        }
    }
}

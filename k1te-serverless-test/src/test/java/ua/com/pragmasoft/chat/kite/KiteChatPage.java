/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat.kite;

import static com.microsoft.playwright.assertions.LocatorAssertions.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.AriaRole;
import java.nio.file.Path;
import java.util.regex.Pattern;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.ChatPage;

public final class KiteChatPage extends ChatPage {
  private final Locator messages;
  private final Locator incomingMessages;
  private final Locator outgoingMessages;
  private final Locator fileAttachment;
  private final Locator input;
  private final Locator sendButton;
  private final Locator errorMessages;
  private final Locator menuItems;
  private final Locator editMessage;

  private KiteChatPage(Page page) {
    super(page);
    Locator chat = page.locator("#kite-dialog");

    this.messages = page.locator("kite-msg");
    this.incomingMessages = page.locator("kite-msg:not([status])");
    this.outgoingMessages = page.locator("kite-msg[status]");

    Locator footer = chat.locator("kite-chat-footer");
    this.fileAttachment = footer.locator("label");

    this.input = footer.getByRole(AriaRole.TEXTBOX);
    this.sendButton = footer.locator("svg").filter(new Locator.FilterOptions().setHasText("Send"));

    this.errorMessages = page.locator("kite-toast-notification[type=error]");
    this.menuItems = chat.locator("kite-context-menu").getByRole(AriaRole.LISTITEM);
    this.editMessage = footer.locator(".edit-message");
  }

  public static KiteChatPage of(Page page, String kiteUrl) {
    page.navigate(kiteUrl);
    Locator chatButton = page.locator("#kite-toggle");
    assertThat(chatButton).isVisible();
    chatButton.click();
    return new KiteChatPage(page);
  }

  @Override
  public KiteChatMessage lastMessage(MessageType type) {
    return switch (type) {
      case IN -> new KiteChatMessage(this.incomingMessages.last());
      case OUT -> new KiteChatMessage(this.outgoingMessages.last());
    };
  }

  @Override
  public void sendMessage(String text) {
    this.input.fill(text);
    this.sendButton.click();
    this.lastMessage(MessageType.OUT).hasText(text).waitMessageToBeUploaded(10_000);
  }

  @Override
  public String uploadFile(Path pathToFile) {
    String fileName = this.attachFile(pathToFile);

    this.lastMessage(MessageType.OUT).hasFile(fileName).waitMessageToBeUploaded(15_000);

    return fileName;
  }

  @Override
  public void uploadPhoto(Path pathToPhoto) {
    this.attachFile(pathToPhoto);

    this.lastMessage(MessageType.OUT).isPhoto().waitMessageToBeUploaded(15_000);
  }

  public int messagesCount() {
    return this.messages.count();
  }

  public void hasErrorMessage(String expectedErrorMessage) {
    Locator errorMessage = this.errorMessages.last().locator(".message");

    assertThat(errorMessage).hasText(Pattern.compile(expectedErrorMessage));
  }

  public void editMessage(KiteChatMessage message, String newText) {
    this.chooseMessageMenuItem(message, MenuItem.EDIT);
    assertThat(this.editMessage).isVisible();
    this.input.clear();
    this.input.fill(newText);
    this.sendButton.click();
    message.hasText(newText);
  }

  public void deleteMessage(KiteChatMessage message) {
    this.chooseMessageMenuItem(message, MenuItem.DELETE);
    assertThat(message.locator()).hasCount(0);
  }

  private void chooseMessageMenuItem(KiteChatMessage message, MenuItem item) {
    message.locator().dispatchEvent("click"); // todo currently simple click doesn't work
    this.page.waitForTimeout(500); // May not show context menu
    this.menuItems
        .filter(new Locator.FilterOptions().setHasText(Pattern.compile(item.value)))
        .click();
  }

  private String attachFile(Path path) {
    FileChooser fileChooser = this.page.waitForFileChooser(this.fileAttachment::click);
    this.page.waitForTimeout(500); // May not show context menu
    fileChooser.setFiles(path);
    return path.getFileName().toString();
  }

  public static class KiteChatMessage implements ChatMessage {
    private Locator messageLocator;

    private KiteChatMessage(Locator messageLocator) {
      this.messageLocator = messageLocator;
    }

    @Override
    public Locator locator() {
      return this.messageLocator;
    }

    @Override
    public KiteChatMessage hasText(String expected) {
      assertThat(this.messageLocator)
          .hasText(
              Pattern.compile(expected),
              new HasTextOptions().setUseInnerText(true).setTimeout(10_000));
      return this;
    }

    @Override
    public KiteChatMessage hasFile(String expectedFileName) {
      Locator fileLocator = this.messageLocator.locator("kite-file").getByRole(AriaRole.LINK);

      assertThat(fileLocator)
          .hasAttribute(
              "download",
              Pattern.compile(expectedFileName),
              new HasAttributeOptions().setTimeout(10_000));
      return this;
    }

    @Override
    public KiteChatMessage isPhoto() {
      Locator photoLocator =
          this.messageLocator.locator("kite-file").getByRole(AriaRole.LINK).locator("img");

      assertThat(photoLocator).isVisible(new IsVisibleOptions().setTimeout(10_000));
      return this;
    }

    @Override
    public void waitMessageToBeUploaded(double timeout) {
      assertThat(this.messageLocator)
          .not()
          .hasAttribute("status", "unknown", new HasAttributeOptions().setTimeout(timeout));
    }

    @Override
    public KiteChatMessage snapshot() {
      String messageId = this.messageLocator.getAttribute("messageid");
      String messageByIdSelector = "kite-msg[messageid=\"" + messageId + "\"]";
      this.messageLocator = this.messageLocator.page().locator(messageByIdSelector);
      return this;
    }
  }

  private enum MenuItem {
    EDIT("Edit"),
    DELETE("Delete"),
    SHARE("Share"),
    COPY("Copy"),
    SELECT("Select"),
    SELECT_ALL("Select all");

    final String value;

    MenuItem(String value) {
      this.value = value;
    }
  }
}

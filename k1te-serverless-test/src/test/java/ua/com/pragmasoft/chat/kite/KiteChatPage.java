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

/**
 * Represents a chat page in the Kite application for automation testing.
 *
 * <p>This class extends the {@link ChatPage} class and provides specific implementations for
 * interacting with the Kite chat.
 *
 * <p><strong>Important:</strong> To ensure proper functionality of the {@code edit()} and {@code delete()}
 * methods, it is recommended to invoke the {@code snapshot()} method on the {@linkplain  KiteChatMessage}
 * instance before passing it. This ensures that the instance refers to a specific message in the chat,
 * even if new messages have been added since the instance was created.
 *
 * <p>The {@code uploadFile()} and {@code uploadPhoto()} methods and {@code sendMessage()} method automatically wait until the message
 * is displayed in the chat, enhancing reliability in automation test scenarios.
 *
 * @see ChatPage
 * @see ChatMessage
 */
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

  /**
   * Constructs a new {@code KiteChatPage} associated with the provided Playwright {@code Page}.
   * It also creates necessary locator which are used by this class's methods.
   * This constructor is private; instances should be created using the {@code of} method.
   *
   * @param page The Playwright {@code Page} instance associated with the Kite chat page.
   * @see #of(Page, String)
   */
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

  /**
   * Creates a new {@code KiteChatPage} instance by navigating to the specified Kite URL and
   * initializing the required locators.
   *
   * <p>This method is a factory method that ensures proper setup of the chat page.
   *
   * @param page    The Playwright {@code Page} instance to navigate and interact with.
   * @param kiteUrl The URL of the Kite chat application.
   * @return A new {@code KiteChatPage} instance for the specified Kite chat.
   */
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

  /**
   * Sends a text message in the chat and waits for it to appear in the chat.
   *
   * @param text  The text of the message to be sent.
   */
  @Override
  public void sendMessage(String text) {
    this.input.fill(text);
    this.sendButton.click();
    this.lastMessage(MessageType.OUT).hasText(text).waitMessageToBeUploaded(10_000);
  }

  /**
   * Uploads a file to the chat and waits for it to appear in the chat.
   *
   * @param pathToFile  The path to the file to be uploaded.
   * @return A string representing the uploaded file's name or identifier.
   */
  @Override
  public String uploadFile(Path pathToFile) {
    String fileName = this.attachFile(pathToFile);

    this.lastMessage(MessageType.OUT).hasFile(fileName).waitMessageToBeUploaded(15_000);

    return fileName;
  }

  /**
   * Uploads a photo to the chat and waits for it to appear in the chat.
   *
   * @param pathToPhoto  The path to the photo file to be uploaded.
   */
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

  /**
   * Edits the content of a specific chat message identified by the provided KiteChatMessage instance.
   * Before invoking this method, ensure to call snapshot() on the target message instance to lock it
   * to a specific message in the chat.
   *
   * @param message  The KiteChatMessage instance representing the message to be edited.
   * @param newText  The new text content to replace the existing message text.
   */
  public void editMessage(KiteChatMessage message, String newText) {
    this.chooseMessageMenuItem(message, MenuItem.EDIT);
    assertThat(this.editMessage).isVisible();
    this.input.clear();
    this.input.fill(newText);
    this.sendButton.click();
    message.hasText(newText);
  }

  /**
   * Deletes a specific chat message identified by the provided KiteChatMessage instance.
   * Prior to using this method, make sure to call snapshot() on the intended message instance to
   * lock it to a particular message in the chat.
   *
   * @param message  The KiteChatMessage instance representing the message to be deleted.
   */
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

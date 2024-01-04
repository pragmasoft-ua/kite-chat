/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.chat.telegram;

import static com.microsoft.playwright.assertions.LocatorAssertions.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.*;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.MouseButton;
import java.nio.file.Path;
import java.util.regex.Pattern;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.ChatPage;

/**
 * Represents a chat page in the Telegram Web application for automation testing.
 * Extends the {@link ChatPage} abstract class to interact with the Telegram chat interface.
 * Provides functionalities to send messages, upload files/photos, edit, delete, and reply to messages.
 *
 * <p><strong>Important:</strong> To ensure proper functionality of the {@code edit()} and {@code delete()}
 * methods, it is recommended to invoke the {@code snapshot()} method on the {@link TelegramChatMessage}
 * instance before passing it. This ensures that the instance refers to a specific message in the chat,
 * even if new messages have been added since the instance was created.
 *
 * @see ChatPage
 * @see ChatMessage
 */
public final class TelegramChatPage extends ChatPage {
  private static final String TELEGRAM_WEB_URL = "https://web.telegram.org";

  private final Locator incomingMessages;
  private final Locator outgoingMessages;
  private final Locator fileAttachment;
  private final Locator sendFileButton;
  private final Locator input;
  private final Locator sendTextButton;
  private final Locator menuItems;
  private final Locator reply;
  private final Locator deleteForAll;
  private final Locator deleteButton;

  private TelegramChatPage(Page page) {
    super(page);
    Locator chat = page.locator("#column-center").locator("div.chat");

    Locator messageGroups = chat.locator("div.bubbles").locator("div.bubbles-group");
    this.incomingMessages = messageGroups.locator(".bubble.is-in");
    this.outgoingMessages = messageGroups.locator(".bubble.is-out");

    this.fileAttachment = page.locator(".attach-file");
    this.sendFileButton =
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send"));

    this.input = chat.locator("div.input-message-input:not(.input-field-input-fake)");
    this.sendTextButton = chat.locator("button.send").or(chat.locator("button.edit"));

    this.menuItems = page.locator(".btn-menu-items >> .btn-menu-item");
    this.reply = chat.locator(".reply >> .reply-title").last();
    this.deleteForAll = page.locator(".popup >> label");
    this.deleteButton =
        page.locator(".popup")
            .getByRole(AriaRole.BUTTON, new Locator.GetByRoleOptions().setName("Delete"));
  }

  /**
   * Creates an instance of TelegramChatPage associated with the provided Playwright Page and navigates to the specified chat.
   * This method is typically used to initialize a TelegramChatPage for a specific chat session.
   *
   * @param page      The Playwright Page instance associated with the Telegram web application.
   * @param chatTitle The title of the chat to be opened.
   * @return A new instance of TelegramChatPage associated with the specified chat.
   */
  public static TelegramChatPage of(Page page, String chatTitle) {
    // TODO: 04.01.2024
    page.navigate(TELEGRAM_WEB_URL);
    Locator chatList = page.locator(".chatlist-top").getByRole(AriaRole.LINK);
    Locator chat =
        chatList.filter(
            new Locator.FilterOptions()
                .setHas(
                    page.locator(
                        "div.user-title", new Page.LocatorOptions().setHasText(chatTitle))));

    assertThat(chat).hasCount(1, new HasCountOptions().setTimeout(30_000));
    chat.click();
    return new TelegramChatPage(page);
  }

  @Override
  public TelegramChatMessage lastMessage(MessageType type) {
    return switch (type) {
      case IN -> new TelegramChatMessage(this.incomingMessages.last());
      case OUT -> new TelegramChatMessage(this.outgoingMessages.last());
    };
  }

  /**
   * Sends a text message in the chat and waits for it to appear in the chat.
   *
   * @param text The text of the message to be sent.
   */
  @Override
  public void sendMessage(String text) {
    this.input.fill(text);
    this.sendTextButton.click(new Locator.ClickOptions().setForce(true));
    this.lastMessage(MessageType.OUT).hasText(text);
  }

  /**
   * Uploads a file to the chat and waits for it to be uploaded in the chat.
   *
   * @param pathToFile The path to the file to be uploaded.
   * @return A string representing the uploaded file's name or identifier.
   */
  @Override
  public String uploadFile(Path pathToFile) {
    String fileName = this.attachFile(pathToFile, AttachmentType.DOC);

    this.lastMessage(MessageType.OUT).hasFile(fileName).waitMessageToBeUploaded(15_000);

    return fileName;
  }

  /**
   * Uploads a photo to the chat and waits for it to be uploaded in the chat.
   *
   * @param pathToPhoto The path to the photo file to be uploaded.
   */
  @Override
  public void uploadPhoto(Path pathToPhoto) {
    this.attachFile(pathToPhoto, AttachmentType.PHOTO);

    this.lastMessage(MessageType.OUT).isPhoto().waitMessageToBeUploaded(15_000);
  }

  /**
   * Replies to a specific message in the Telegram chat.
   * This method ensures synchronization by waiting for the reply UI and sends a text message as a reply to a target message.
   * Before invoking this method, ensure to call snapshot() on the target message instance to lock it
   * to a specific message in the chat.
   *
   * @param message The TelegramChatMessage instance representing the message to which to reply.
   * @param text    The text of the reply message.
   */
  public void replyMessage(TelegramChatMessage message, String text) {
    this.chooseMessageMenuItem(message, MenuItem.REPLY);
    assertThat(this.reply).hasText(Pattern.compile("Reply to"));
    assertThat(this.reply).isVisible();
    this.sendMessage(text);
  }

  /**
   * Edits the content of a specific chat message identified by the provided TelegramChatMessage instance.
   * This method ensures synchronization by waiting for the editing UI to be visible before proceeding.
   * Before invoking this method, ensure to call snapshot() on the target message instance to lock it
   * to a specific message in the chat.
   *
   * @param message The TelegramChatMessage instance representing the message to be edited.
   * @param newText The new text content to replace the existing message text.
   */
  public void editMessage(TelegramChatMessage message, String newText) {
    this.chooseMessageMenuItem(message, MenuItem.EDIT);
    assertThat(this.reply).hasText(Pattern.compile("Editing"));
    assertThat(this.reply).isVisible();
    this.input.clear();
    this.input.fill(newText);
    this.sendTextButton.click();
    message.hasText(newText);
  }

  /**
   * Deletes a specific chat message identified by the provided TelegramChatMessage instance.
   * Before invoking this method, ensure to call snapshot() on the target message instance to lock it
   * to a specific message in the chat.
   *
   * @param message The TelegramChatMessage instance representing the message to be deleted.
   */
  public void deleteMessage(TelegramChatMessage message) {
    this.chooseMessageMenuItem(message, MenuItem.DELETE);
    assertThat(this.deleteForAll).isVisible();
    deleteForAll.click();
    page.waitForTimeout(100);
    deleteButton.click();
    assertThat(message.locator()).hasCount(0);
  }

  private void chooseMessageMenuItem(TelegramChatMessage message, MenuItem item) {
    message.locator().click(new Locator.ClickOptions().setButton(MouseButton.RIGHT));
    this.page.waitForTimeout(100); // May not show context menu
    this.menuItems.filter(new Locator.FilterOptions().setHasText(item.value)).click();
  }

  private String attachFile(Path path, AttachmentType attachment) {
    FileChooser fileChooser =
        page.waitForFileChooser(
            () -> {
              this.fileAttachment.click();
              this.page.waitForTimeout(100); // If not wait,file attachment may not be invoked
              this.fileAttachment
                  .locator(".btn-menu-item")
                  .filter(new Locator.FilterOptions().setHasText(attachment.type))
                  .click();
            });
    fileChooser.setFiles(path);
    this.sendFileButton.click();
    this.page.waitForTimeout(500);

    return path.getFileName().toString();
  }

  public static class TelegramChatMessage implements ChatMessage {
    private Locator messageLocator;

    private TelegramChatMessage(Locator messageLocator) {
      assertThat(messageLocator).hasClass(Pattern.compile("bubble"));
      this.messageLocator = messageLocator;
    }

    @Override
    public Locator locator() {
      return this.messageLocator;
    }

    @Override
    public TelegramChatMessage hasText(String expected) {
      Locator textMessage = this.messageLocator.locator(".message");
      assertThat(textMessage)
          .hasText(Pattern.compile(expected), new HasTextOptions().setUseInnerText(true));
      return this;
    }

    @Override
    public TelegramChatMessage hasFile(String expectedFileName) {
      Locator documentName = this.messageLocator.locator(".document-name");
      assertThat(documentName)
          .hasText(expectedFileName, new HasTextOptions().setUseInnerText(true));
      return this;
    }

    @Override
    public TelegramChatMessage isPhoto() {
      Locator photo = this.messageLocator.locator(".attachment >> img.media-photo");
      assertThat(photo).isVisible();
      return this;
    }

    @Override
    public void waitMessageToBeUploaded(double timeout) {
      Locator loader = this.messageLocator.locator(".preloader-container");
      assertThat(loader).not().isVisible(new IsVisibleOptions().setTimeout(timeout));
    }

    @Override
    public TelegramChatMessage snapshot() {
      // Sometimes data-mid may not be set immediately or be the same as the previous message
      // because of it we need to wait a little bit
      Page page = this.messageLocator.page(); // Closed with context
      page.waitForTimeout(500);
      String messageId = this.messageLocator.getAttribute("data-mid");
      String messageByIdSelector = ".bubble[data-mid=\"" + messageId + "\"]";
      this.messageLocator = page.locator(messageByIdSelector);
      return this;
    }
  }

  private enum AttachmentType {
    DOC("Document"),
    PHOTO("Photo or Video"),
    POLL("Poll");

    final String type;

    AttachmentType(String type) {
      this.type = type;
    }
  }

  private enum MenuItem {
    REPLY("Reply"),
    EDIT("Edit"),
    PIN("Pin"),
    DOWNLOAD("Download"),
    FORWARD("Forward"),
    SELECT("Select"),
    DELETE("Delete");

    final String value;

    MenuItem(String item) {
      this.value = item;
    }
  }
}

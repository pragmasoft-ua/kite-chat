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

  public static TelegramChatPage of(Page page, String chatTitle) {
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

  @Override
  public void sendMessage(String text) {
    this.input.fill(text);
    this.sendTextButton.click(new Locator.ClickOptions().setForce(true));
    this.lastMessage(MessageType.OUT).hasText(text);
  }

  @Override
  public String uploadFile(Path pathToFile) {
    String fileName = this.attachFile(pathToFile, AttachmentType.DOC);

    this.lastMessage(MessageType.OUT).hasFile(fileName).waitMessageToBeUploaded(15_000);

    return fileName;
  }

  @Override
  public void uploadPhoto(Path pathToPhoto) {
    this.attachFile(pathToPhoto, AttachmentType.PHOTO);

    this.lastMessage(MessageType.OUT).isPhoto().waitMessageToBeUploaded(15_000);
  }

  public void replyMessage(TelegramChatMessage message, String text) {
    this.chooseMessageMenuItem(message, MenuItem.REPLY);
    assertThat(this.reply).hasText(Pattern.compile("Reply to"));
    assertThat(this.reply).isVisible();
    this.sendMessage(text);
  }

  public void editMessage(TelegramChatMessage message, String newText) {
    this.chooseMessageMenuItem(message, MenuItem.EDIT);
    assertThat(this.reply).hasText(Pattern.compile("Editing"));
    assertThat(this.reply).isVisible();
    this.input.clear();
    this.input.fill(newText);
    this.sendTextButton.click();
    message.hasText(newText);
  }

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

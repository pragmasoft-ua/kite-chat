package ua.com.pragmasoft.chat.kite;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions.IsVisibleOptions;
import com.microsoft.playwright.options.AriaRole;
import ua.com.pragmasoft.chat.ChatMessage;
import ua.com.pragmasoft.chat.ChatPage;

import java.nio.file.Path;
import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.LocatorAssertions.*;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class KiteChatPage implements ChatPage {
    private final Page page;
    private final Locator incomingMessages;
    private final Locator outgoingMessages;
    private final Locator fileAttachment;
    private final Locator input;
    private final Locator sendButton;

    private KiteChatPage(Page page) {
        this.page = page;
        Locator chat = page.locator("#kite-dialog");

        this.incomingMessages = page.locator("kite-msg:not([status])");
        this.outgoingMessages = page.locator("kite-msg[status]");

        Locator footer = chat.locator("kite-chat-footer");
        this.fileAttachment = footer.locator("label");
        this.input = footer.getByRole(AriaRole.TEXTBOX);
        this.sendButton = footer.locator("svg")
            .filter(new Locator.FilterOptions().setHasText("Send"));
    }

    public static KiteChatPage of(Page page, String kiteUrl) {
        page.navigate(kiteUrl);
        Locator chatButton = page.locator("#kite-toggle");
        assertThat(chatButton).isVisible();
        chatButton.click();
        return new KiteChatPage(page);
    }

    @Override
    public Page getPage() {
        return this.page;
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
        this.lastMessage(MessageType.OUT)
            .hasText(text)
            .waitMessageToBeUploaded(5000);
    }

    @Override
    public UploadStatus uploadFile(Path pathToFile) {
        String fileName = this.attachFile(pathToFile);

        KiteChatMessage fileMessage = this.lastMessage(MessageType.OUT);
        fileMessage.hasFile(fileName)
            .waitMessageToBeUploaded(15_000);

        String status = fileMessage.message.getAttribute("status");
        return new UploadStatus(fileName, status.equals("delivered"));
    }

    @Override
    public void uploadPhoto(Path pathToPhoto) {
        this.attachFile(pathToPhoto);

        this.lastMessage(MessageType.OUT)
            .isPhoto()
            .waitMessageToBeUploaded(15_000);
    }

    private String attachFile(Path path) {
        FileChooser fileChooser = this.page.waitForFileChooser(this.fileAttachment::click);
        fileChooser.setFiles(path);
        return path.getFileName().toString();
    }

    public static class KiteChatMessage implements ChatMessage {
        private final Locator message;

        private KiteChatMessage(Locator message) {
            this.message = message;
        }

        @Override
        public ChatMessage hasText(String expected, double timeout) {
            assertThat(this.message).hasText(Pattern.compile(expected),
                new HasTextOptions()
                    .setTimeout(timeout)
                    .setUseInnerText(true)
                    .setIgnoreCase(true));
            return this;
        }

        @Override
        public ChatMessage hasFile(String expectedFileName, double timeout) {
            Locator fileLocator = this.message
                .locator("kite-file")
                .getByRole(AriaRole.LINK);

            assertThat(fileLocator).hasAttribute("download", Pattern.compile(expectedFileName),
                new HasAttributeOptions().setTimeout(timeout));
            return this;
        }

        @Override
        public ChatMessage isPhoto(double timeout) {
            Locator photoLocator = this.message.locator("kite-file")
                .getByRole(AriaRole.LINK)
                .locator("img");

            assertThat(photoLocator)
                .isVisible(new IsVisibleOptions().setTimeout(timeout));
            return this;
        }

        @Override
        public void waitMessageToBeUploaded(double timeout) {
            assertThat(this.message).not().hasAttribute("status", "unknown",
                new HasAttributeOptions().setTimeout(timeout));
        }
    }
}

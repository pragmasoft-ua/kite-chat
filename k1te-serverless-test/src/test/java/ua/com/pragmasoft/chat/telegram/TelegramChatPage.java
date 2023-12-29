/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.chat.telegram;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.FileChooser;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.options.AriaRole;
import ua.com.pragmasoft.chat.ChatPage;

import java.nio.file.Path;
import java.util.regex.Pattern;

public final class TelegramChatPage implements ChatPage {
    private static final String TELEGRAM_WEB_URL = "https://web.telegram.org";

    private final Page page;
    private final Locator messageGroups;
    private final Locator incomingMessages;
    private final Locator outgoingMessages;
    private final Locator fileAttachment;
    private final Locator sendFileButton;
    private final Locator input;
    private final Locator sendTextButton;

    private TelegramChatPage(Page page) {
        this.page = page;
        Locator chat = page.locator("#column-center").locator("div.chat");

        this.messageGroups = chat.locator("div.bubbles").locator("div.bubbles-group");
        this.incomingMessages = this.messageGroups.locator(".bubble.is-in");
        this.outgoingMessages = this.messageGroups.locator(".bubble.is-out");

        this.fileAttachment = page.locator(".attach-file");
        this.sendFileButton =
            page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Send"));

        this.input = chat.locator("div.input-message-input:not(.input-field-input-fake)");
        this.sendTextButton = chat.locator("button.send");
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

        assertThat(chat).hasCount(1);
        chat.click();
        return new TelegramChatPage(page);
    }

    @Override
    public Page getPage() {
        return this.page;
    }

    @Override
    public String lastMessage(MessageType type) {
        String message =
            switch (type) {
                case IN -> this.incomingMessages.last().innerText();
                case OUT -> this.outgoingMessages.last().innerText();
            };
        return message.substring(0, message.lastIndexOf("\n"));
    }

    public TelegramChatMessage lastMessage1(MessageType type) {
        return switch (type) {
            case IN -> new TelegramChatMessage(this.incomingMessages.last());
            case OUT -> new TelegramChatMessage(this.outgoingMessages.last());
        };
    }

    @Override
    public void sendMessage(String text) {
        this.input.fill(text);
        this.sendTextButton.click();
        assertThat(this.outgoingMessages.last()).hasText(
            Pattern.compile(text)); // TODO: 29.12.2023
    }

    public String sendMessageAndWaitResponse(String text) {
        long lastIncomingMessageId =
            Long.parseLong(this.incomingMessages.last().getAttribute("data-mid"));
        this.sendMessage(text);

        int overallTimeout = 0;
        while (lastIncomingMessageId
            == Long.parseLong(this.incomingMessages.last().getAttribute("data-mid"))) {
            if (overallTimeout > 20_000)
                throw new IllegalStateException("Timeout for response is exceeded");
            this.page.waitForTimeout(200);
            overallTimeout += 200;
        }

        return this.lastMessage(MessageType.IN);
    }

    @Override
    public String uploadFile(Path pathToFile) {
        FileChooser fileChooser = page.waitForFileChooser(() -> {
            this.fileAttachment.click();
            this.page.waitForTimeout(100);  //If not wait - document attachment may not be invoked
            this.fileAttachment
                .locator(".btn-menu-item")
                .filter(new Locator.FilterOptions().setHasText("Document"))
                .click();
        });
        fileChooser.setFiles(pathToFile);
        this.sendFileButton.click();

        // TODO: 29.12.2023  
        Locator lastMessage = this.outgoingMessages.last();
        Locator documentIcon = lastMessage.locator(".document-ico");
        Locator documentName = lastMessage.locator(".document-name");
        String fileName = pathToFile.getFileName().toString();

        assertThat(documentName).hasText(fileName);
        assertThat(documentIcon.locator(".preloader-container")) // TODO: 29.12.2023 not to use icon
            .not().isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(25_000));

        return fileName;
    }

    public String uploadPhoto(Path pathToPhoto) {
        FileChooser fileChooser = page.waitForFileChooser(() -> {
            this.fileAttachment.click();
            this.page.waitForTimeout(100); //If not wait - photo attachment may not be invoked
            this.fileAttachment
                .locator(".btn-menu-item")
                .filter(new Locator.FilterOptions().setHasText("Photo or Video"))
                .click();
        });
        fileChooser.setFiles(pathToPhoto);
        this.sendFileButton.click();

        Locator photo = this.outgoingMessages.last().locator("img.media-photo");
        assertThat(photo).isVisible();
        assertThat(this.outgoingMessages.last().locator(".preloader-container"))
            .not().isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(25_000));

        return pathToPhoto.getFileName().toString();
    }
}

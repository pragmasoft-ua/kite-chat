package ua.com.pragmasoft.chat.telegram;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.assertions.LocatorAssertions;

import java.util.regex.Pattern;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

public final class TelegramChatMessage {
    private final Locator message;

    // TODO: 29.12.2023 private class in TgChat
    public TelegramChatMessage(Locator message) {
        //Verify that locator is a Telegram message
        assertThat(message).hasClass(Pattern.compile("bubble"),
            new LocatorAssertions.HasClassOptions().setTimeout(1000));
        this.message = message;
    }

    public void hasText(String expected, double timeout) {
        assertThat(this.message).hasText(Pattern.compile(expected),
            new LocatorAssertions.HasTextOptions()
                .setUseInnerText(true)
                .setIgnoreCase(true)
                .setTimeout(timeout));
    }

    public void hasFile(String expectedFileName, double timeout) {
        Locator documentName = this.message.locator(".document-name");
        assertThat(documentName).hasText(expectedFileName, new LocatorAssertions.HasTextOptions()
            .setUseInnerText(true)
            .setTimeout(timeout));
    }

    public void hasPhoto(String expectedPhotoName, double timeout) {
        // we can not verify photo's name in Telegram since it doesn't provide any photo's metadata
        Locator photo = this.message.locator(".attachment >> img.media-photo");
        assertThat(photo)
            .isVisible(new LocatorAssertions.IsVisibleOptions().setTimeout(timeout));
    }

    public Locator getMessage() {
        return message;
    }
}

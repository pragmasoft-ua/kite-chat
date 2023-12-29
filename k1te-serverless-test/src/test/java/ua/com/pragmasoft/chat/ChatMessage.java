package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Locator;

public interface ChatMessage {

    default ChatMessage hasText(String expected) {
        return this.hasText(expected, 3000);
    }

    ChatMessage hasText(String expected, double timeout);

    default ChatMessage hasFile(String expectedFileName) {
        return this.hasFile(expectedFileName, 3000);
    }

    ChatMessage hasFile(String expectedFileName, double timeout);

    default ChatMessage hasPhoto(String expectedPhotoName) {
        return this.hasPhoto(expectedPhotoName, 3000);
    }

    ChatMessage hasPhoto(String expectedPhotoName, double timeout);

    void waitMessageToBeUploaded(double timeout);
}

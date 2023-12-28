package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.FilePayload;

import java.nio.file.Path;

public interface ChatPage {

    Page getPage();

    String lastMessage(MessageType type);

    void sendMessage(String text);

    String uploadFile(Path pathToFile);

    default String lastMessage() {
        return this.lastMessage(MessageType.ANY);
    }

    default void verifyIncomingMessageText(String expectedValue) {
        this.verifyIncomingMessageText(expectedValue, 2000);
    }

    default void verifyIncomingMessageText(String expectedValue, double timeout) {
        double overallTimeout = 0;
        while (!this.lastMessage(MessageType.IN).contains(expectedValue)) {
            if (overallTimeout > timeout)
                throw new IllegalStateException("Timeout for response is exceeded");
            this.getPage().waitForTimeout(200);
            overallTimeout += 200;
        }
    }


    enum MessageType {
        IN,
        OUT,
        ANY
    }
}

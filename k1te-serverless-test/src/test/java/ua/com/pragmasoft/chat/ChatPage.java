package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;

import java.nio.file.Path;

public interface ChatPage {

    Page getPage();

    String lastMessage(MessageType type);

    void sendMessage(String text);

    String uploadFile(Path pathToFile);

    default void verifyIncomingTextMessage(String expectedValue) {
        this.verifyIncomingTextMessage(expectedValue, 2000);
    }

    default void verifyIncomingTextMessage(String expectedValue, double timeout) {
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
    }
}

package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;

import java.nio.file.Path;

public interface ChatPage {

    Page getPage();

    ChatMessage lastMessage(MessageType type);

    void sendMessage(String text);

    String uploadFile(Path pathToFile);

    enum MessageType {
        IN,
        OUT,
    }
}

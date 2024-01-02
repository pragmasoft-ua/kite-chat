package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;

import java.nio.file.Path;

public abstract class ChatPage {
    protected final Page page;

    protected ChatPage(Page page) {
        this.page = page;
    }

    public abstract ChatMessage lastMessage(MessageType type);

    public abstract void sendMessage(String text);

    public abstract String uploadFile(Path pathToFile);

    public abstract void uploadPhoto(Path pathToPhoto);

    public enum MessageType {
        IN,
        OUT,
    }
}

package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;

import java.nio.file.Path;

public interface ChatPage {

    Page getPage();

    ChatMessage lastMessage(MessageType type);

    void sendMessage(String text);

    UploadStatus uploadFile(Path pathToFile);

    void uploadPhoto(Path pathToPhoto);

    enum MessageType {
        IN,
        OUT,
    }

    record UploadStatus(String fileName, boolean success) {
        public UploadStatus(boolean success) {
            this("", success);
        }
    }
}

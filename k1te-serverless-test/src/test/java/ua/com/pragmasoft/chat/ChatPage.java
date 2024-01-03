/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Page;
import java.nio.file.Path;

/**
 * The abstract base class representing a chat page in an application, designed for automation testing.
 * This class encapsulates common functionalities related to interacting with a chat interface.
 *
 * <p>Subclasses extending this class are expected to implement specific methods for interacting
 * with chat messages, sending messages, and uploading files or photos.
 *
 * <p>The class contains an enumeration {@code MessageType} to distinguish between incoming (IN)
 * and outgoing (OUT) chat messages.
 *
 * @see Page
 */
public abstract class ChatPage {
  /**
   * The underlying {@code Page} associated with the chat page, providing access to general
   * functionalities of a web page or application.
   */
  protected final Page page;

  /**
   * Constructs a new {@code ChatPage} with the specified {@code Page} instance.
   *
   * @param page The {@code Page} instance associated with the chat page.
   */
  protected ChatPage(Page page) {
    this.page = page;
  }

  /**
   * Gets the underlying {@code Page} instance associated with the chat page.
   *
   * @return The {@code Page} instance.
   */
  public Page getPage() {
    return this.page;
  }

  /**
   * Retrieves the last chat message of the specified type.
   *
   * @param type The type of chat message to retrieve (IN for incoming, OUT for outgoing).
   * @return The last chat message of the specified type.
   */
  public abstract ChatMessage lastMessage(MessageType type);

  /**
   * Sends a text message in the chat.
   *
   * @param text The text of the message to be sent.
   */
  public abstract void sendMessage(String text);

  /**
   * Uploads a file to the chat, given the file path.
   *
   * @param pathToFile The path to the file to be uploaded.
   * @return A string representing the fileName of the uploaded file.
   */
  public abstract String uploadFile(Path pathToFile);

  /**
   * Uploads a photo to the chat, given the photo file path.
   *
   * @param pathToPhoto The path to the photo file to be uploaded.
   */
  public abstract void uploadPhoto(Path pathToPhoto);

  /**
   * Enumeration representing the type of chat messages (IN for incoming, OUT for outgoing).
   */
  public enum MessageType {
    IN,
    OUT,
  }
}

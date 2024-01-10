/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Locator;
import ua.com.pragmasoft.chat.kite.KiteChatPage;
import ua.com.pragmasoft.chat.kite.KiteChatPage.KiteChatMessage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage;
import ua.com.pragmasoft.chat.telegram.TelegramChatPage.TelegramChatMessage;

/**
 * Represents a chat message within a chat interface, providing methods for validation and
 * interaction. Implementing classes should define specific behaviors related to text, file
 * attachments, photos, and synchronization of message upload status.
 *
 * <p>The {@code ChatMessage} interface facilitates the validation of various aspects of a chat
 * message, including its text content, attached files, and the presence of photos. It also offers
 * functionality to wait for the message to be uploaded and to create a snapshot for targeting a
 * specific instance of the message in the chat.
 *
 * @see KiteChatMessage
 * @see TelegramChatMessage
 * @see KiteChatPage
 * @see TelegramChatPage
 */
public interface ChatMessage {
  /**
   * Verifies that the text content of the chat message matches the expected text.
   *
   * @param expected The expected text content of the chat message.
   * @return A reference to the same {@code ChatMessage} instance for method chaining.
   */
  ChatMessage hasText(String expected);

  /**
   * Verifies that the file message matches the expected file name.
   *
   * @param expectedFileName The expected file name of the file message in the chat.
   * @return A reference to the same {@code ChatMessage} instance for method chaining.
   */
  ChatMessage hasFile(String expectedFileName);

  /**
   * Verifies that the chat message represents a photo.
   *
   * @return A reference to the same {@code ChatMessage} instance for method chaining.
   */
  ChatMessage isPhoto();

  /**
   * Waits for the chat message to be uploaded within the specified timeout period.
   *
   * @param timeout The maximum time to wait for the message to be uploaded, in milliseconds.
   */
  void waitMessageToBeUploaded(double timeout);

  /**
   * Creates a snapshot of the current state of the chat message. This is especially useful for
   * targeting specific messages within the chat.
   *
   * @return A reference to the same {@code ChatMessage} instance for method chaining.
   */
  ChatMessage snapshot();

  /**
   * Retrieves the locator associated with the chat message. The locator is used to identify and
   * interact with the corresponding element in the chat.
   *
   * @return The locator representing the chat message.
   */
  Locator locator();
}

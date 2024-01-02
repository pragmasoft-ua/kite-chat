/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat;

import com.microsoft.playwright.ElementHandle;

public interface ChatMessage {

  default ChatMessage hasText(String expected) {
    return this.hasText(expected, 3000);
  }

  ChatMessage hasText(String expected, double timeout);

  default ChatMessage hasFile(String expectedFileName) {
    return this.hasFile(expectedFileName, 6000);
  }

  ChatMessage hasFile(String expectedFileName, double timeout);

  default ChatMessage isPhoto() {
    return this.isPhoto(6000);
  }

  ChatMessage isPhoto(double timeout);

  void waitMessageToBeUploaded(double timeout);

  ElementHandle element();
}

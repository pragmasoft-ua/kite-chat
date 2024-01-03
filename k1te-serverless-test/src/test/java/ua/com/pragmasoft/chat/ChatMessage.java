/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.chat;

import com.microsoft.playwright.Locator;

public interface ChatMessage {

  ChatMessage hasText(String expected);

  ChatMessage hasFile(String expectedFileName);

  ChatMessage isPhoto();

  void waitMessageToBeUploaded(double timeout);

  ChatMessage snapshot();

  Locator locator();
}

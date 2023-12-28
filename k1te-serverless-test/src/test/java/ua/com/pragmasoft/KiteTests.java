/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.ChatPage;

import static ua.com.pragmasoft.chat.ChatPage.MessageType.*;

@Tag("kite")
class KiteTests extends BaseTest {

    @Test
    void test() {
        String helloMessage = "hello 123";
        kiteChat.sendMessage(helloMessage);
        telegramChat.verifyIncomingMessageText(helloMessage);

        String hiMessage = "hi!";
        telegramChat.sendMessage(hiMessage);
        kiteChat.verifyIncomingMessageText(hiMessage);
    }

}

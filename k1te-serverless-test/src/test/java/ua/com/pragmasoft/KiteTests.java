/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.ChatPage;
import ua.com.pragmasoft.chat.ChatPage.MessageType;

import java.nio.file.Path;

@Tag("kite")
class KiteTests extends BaseTest {

    @Test
    void test3(){
        kiteChat.hasErrorMessage("size exceeds 20.00 MB limit");
    }

    @Test
    void test() {
        String hello = "Hello!";
        kiteChat.sendMessage(hello);
        telegramChat.lastMessage(MessageType.IN)
            .hasText(hello);

        String file = kiteChat.uploadFile(Path.of(BASE_PATH, "sample.pdf"));
        telegramChat.lastMessage(MessageType.IN)
            .hasFile(file);

        kiteChat.uploadPhoto(Path.of(BASE_PATH, "sample.jpg"));
        telegramChat.lastMessage(MessageType.IN)
            .isPhoto();
    }

    @Test
    void test2() {
        String hello = "hello!";
        kiteChat.sendMessage(hello);
        telegramChat.lastMessage(MessageType.IN)
            .hasText(hello);

        String hostHello = "hello, I'm host";
        telegramChat.sendMessage(hostHello);
        kiteChat.lastMessage(MessageType.IN)
            .hasText(hostHello);

        String file = "Here is file";
        telegramChat.sendMessage(file);
        String fileName = telegramChat.uploadFile(Path.of(BASE_PATH, "sample.pdf"));
        kiteChat.lastMessage(MessageType.IN)
            .hasFile(fileName);


        kiteChat.sendMessage("Thanks!");
        kiteChat.sendMessage("Here is my file");
        String  file1 = kiteChat.uploadFile(Path.of(BASE_PATH, "sample.pdf"));
        telegramChat.lastMessage(MessageType.IN)
            .hasFile(file1);

        telegramChat.uploadPhoto(Path.of(BASE_PATH, "sample.jpg"));
        kiteChat.lastMessage(MessageType.IN)
            .isPhoto();
        String nicePhoto = "Nice photo";
        kiteChat.sendMessage(nicePhoto);
        telegramChat.lastMessage(MessageType.IN)
            .hasText(nicePhoto);

        kiteChat.uploadPhoto(Path.of(BASE_PATH, "sample.jpg"));
        telegramChat.lastMessage(MessageType.IN)
            .isPhoto();

        System.out.println();
    }

}

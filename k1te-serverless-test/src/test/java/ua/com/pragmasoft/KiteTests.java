/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft;

import org.junit.jupiter.api.*;
import ua.com.pragmasoft.chat.ChatPage.UploadStatus;

import java.nio.file.Path;

@Tag("kite")
class KiteTests extends BaseTest {

    @Test
    void test() {
        //        String hello = "hello!";
        //        kiteChat.sendMessage(hello);
        //        kiteChat.lastMessage(MessageType.OUT)
        //            .hasText(hello);

        kiteChat.uploadPhoto(Path.of("src/test/resources/sample.jpg"));
        UploadStatus uploadStatus =
            kiteChat.uploadFile(Path.of("src/test/resources/sample.pdf"));
        System.out.println(uploadStatus.success());
        UploadStatus uploadStatus1 =
            kiteChat.uploadFile(Path.of("src/test/resources/ZoomInstallerFull.exe"));
        System.out.println(uploadStatus1.success());

        //
        //        kiteChat.uploadPhoto(Path.of("src/test/resources/sample.jpg"));
        //        kiteChat.lastMessage(MessageType.OUT)
        //            .isPhoto();
    }

}

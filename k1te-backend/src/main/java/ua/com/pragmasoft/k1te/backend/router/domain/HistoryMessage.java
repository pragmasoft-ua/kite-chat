/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.time.Instant;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.*;

public interface HistoryMessage {
  static final String DELIMITER = "##";

  String getChannelName();

  String getMemberId();

  String getMessageId();

  String getContent();

  Instant getTime();

  boolean isIncoming();

  static String encode(MessagePayload payload) {
    String content = null;
    if (payload.type() == Payload.Type.TXT) {
      content = ((PlaintextMessage) payload).text();
    } else if (payload.type() == Payload.Type.BIN) {
      BinaryPayload binaryPayload = (BinaryPayload) payload;
      content =
          binaryPayload.fileName()
              + DELIMITER
              + binaryPayload.fileType()
              + DELIMITER
              + binaryPayload.fileSize()
              + DELIMITER
              + binaryPayload.uri();
    }
    return content;
  }

  static MessagePayload decode(String content) {
    MessagePayload payload;
    if (content.contains(DELIMITER)) {
      String[] args = content.split(DELIMITER);
      if (args.length < 4) {
        throw new IllegalStateException("Binary message is invalid");
      }
      String fileName = args[0];
      String fileType = args[1];
      long fileSize = Long.parseLong(args[2]);
      String url = args[3];
      payload = new BinaryMessage(url, fileName, fileType, fileSize);
    } else {
      payload = new PlaintextMessage(content);
    }
    return payload;
  }
}

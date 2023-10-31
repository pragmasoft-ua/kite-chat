/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.ws;

import java.time.Instant;
import java.time.ZoneId;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryPayload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadResponse;

public interface ObjectStore {

  UploadResponse presign(UploadRequest binaryPayload, String channelName, String memberId);

  BinaryPayload copyTransient(BinaryPayload transientPayload, String channelName, String memberId);

  default String objectName(
      String channelName, String memberId, String simpleName, Instant timestamp) {
    return String.format(
        "%s/%s/%tF/%s",
        channelName, memberId, timestamp.atZone(ZoneId.systemDefault()), simpleName);
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStoreTest {

  static final Logger log = LoggerFactory.getLogger(S3ObjectStoreTest.class);
  final S3AsyncClient s3Client = S3AsyncClient.create();
  final S3Presigner presigner = S3Presigner.create();
  final S3ObjectStore objectStore =
      new S3ObjectStore(
          "prod-k1te-chat-object-store-20231009204819643700000001", s3Client, presigner);

  @Test
  void testObjectName() {
    String objectName =
        this.objectStore.objectName(
            "pragmasoft", "zdv", "Iceland.jpg", Instant.parse("2023-10-18T00:00:00Z"));
    assertEquals("pragmasoft/zdv/2023-10-18/Iceland.jpg", objectName);
  }
}

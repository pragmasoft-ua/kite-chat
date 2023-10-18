package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import software.amazon.awssdk.services.s3.S3Client;

class S3ObjectStoreTest {

  static final Logger log = LoggerFactory.getLogger(S3ObjectStoreTest.class);
  final S3Client s3Client = S3Client.create();
  final S3ObjectStore objectStore = new S3ObjectStore("prod-k1te-chat-object-store-20231009204819643700000001",
      s3Client);

  @Test
  void testObjectName() {
    String objectName = this.objectStore.objectName("pragmasoft", "zdv", "Iceland.jpg",
        Instant.parse("2023-10-18T00:00:00Z"));
    assertEquals("pragmasoft/zdv/2023-10-18/Iceland.jpg", objectName);
  }

}

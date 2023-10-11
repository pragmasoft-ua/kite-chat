package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class S3ObjectStoreTest {

  static final Logger log = LoggerFactory.getLogger(S3ObjectStoreTest.class);
  final S3ObjectStore objectStore = new S3ObjectStore("prod-k1te-chat-object-store-20231009204819643700000001");

  @Test
  void testPresignedUrl() {
    final var url = this.objectStore.store("testFile.png", "image/png", 1024);
    log.info(url.toString());
    assertTrue(url.toString().contains("testFile.png"));
  }

}

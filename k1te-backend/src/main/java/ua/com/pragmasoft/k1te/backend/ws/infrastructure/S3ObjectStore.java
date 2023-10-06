package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;

public final class S3ObjectStore implements ObjectStore {

  private final String bucket;

  public S3ObjectStore(String bucket) {
    this.bucket = bucket;
  }

  @Override
  public URI store(String fileName, String fileType, long fileSize) {
    try (S3Presigner presigner = S3Presigner.create()) {

      var presignRequest = PutObjectPresignRequest.builder()
          .signatureDuration(Duration.ofMinutes(60))
          .putObjectRequest(b -> b.bucket(this.bucket)
              .key(fileName)
              .contentType(fileType)
              .build())
          .build();

      var presignedRequest = presigner.presignPutObject(presignRequest);
      return presignedRequest.url().toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }
}

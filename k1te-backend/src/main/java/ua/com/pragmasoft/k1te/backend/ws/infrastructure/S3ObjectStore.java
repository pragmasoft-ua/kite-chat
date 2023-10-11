package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;

import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;

public final class S3ObjectStore implements ObjectStore {

  static final S3Presigner presigner = S3Presigner.create();

  private final String bucketName;

  public S3ObjectStore(String bucketName) {
    this.bucketName = bucketName;
  }

  @Override
  public URI store(String fileName, String fileType, long fileSize) {

    var presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(60))
        .putObjectRequest(b -> b.bucket(this.bucketName)
            .key(fileName)
            .contentType(fileType)
            .contentLength(fileSize)
            .build())
        .build();

    var presignedRequest = presigner.presignPutObject(presignRequest);
    try {
      return presignedRequest.url().toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }
}

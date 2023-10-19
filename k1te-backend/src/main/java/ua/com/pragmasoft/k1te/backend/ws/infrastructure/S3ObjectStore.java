package ua.com.pragmasoft.k1te.backend.ws.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryPayload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadResponse;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;

public final class S3ObjectStore implements ObjectStore {

  private static final Duration SIGNATURE_VALIDITY = Duration.ofMinutes(60);

  static final S3Presigner presigner = S3Presigner.create();

  private final String bucketName;

  private final S3Client s3Client;

  public S3ObjectStore(String bucketName, S3Client s3Client) {
    this.bucketName = bucketName;
    this.s3Client = s3Client;
  }

  @Override
  public UploadResponse presign(UploadRequest uploadRequest, String channelName, String memberId) {
    String objectName = this.objectName(channelName, memberId, uploadRequest.fileName(), uploadRequest.created());
    String mimeType = uploadRequest.fileType();
    URI canonicalUri = presignedGetUri(objectName);
    URI uploadUri = presignedPutUri(objectName, mimeType);
    return new UploadResponse(uploadRequest.messageId(), canonicalUri, uploadUri);
  }

  @Override
  public BinaryPayload copyTransient(BinaryPayload transientPayload, String channelName, String memberId) {
    String objectName = this.objectName(channelName, memberId, transientPayload.fileName(), transientPayload.created());
    try (var readFrom = transientPayload.uri().toURL().openStream()) {
      this.s3Client.putObject(
          PutObjectRequest
              .builder()
              .bucket(this.bucketName)
              .key(objectName)
              .contentType(transientPayload.fileType())
              .expires(transientPayload.created().plus(365, ChronoUnit.DAYS))
              .cacheControl("max-age: 31536000, immutable")
              .build(),
          RequestBody.fromInputStream(readFrom, transientPayload.fileSize()));
      return new BinaryMessage(this.presignedGetUri(objectName), transientPayload.fileName(),
          transientPayload.fileType(), transientPayload.fileSize(), transientPayload.messageId(),
          transientPayload.created());
    } catch (IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private URI presignedGetUri(String objectName) {
    var presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(SIGNATURE_VALIDITY)
        .getObjectRequest(b -> b.bucket(this.bucketName)
            .key(objectName)
            .build())
        .build();

    var presignedRequest = presigner.presignGetObject(presignRequest);
    try {
      return presignedRequest.url().toURI();
    } catch (URISyntaxException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  private URI presignedPutUri(String objectName, String mimeType) {
    var presignRequest = PutObjectPresignRequest.builder()
        .signatureDuration(SIGNATURE_VALIDITY)
        .putObjectRequest(b -> b.bucket(this.bucketName)
            .key(objectName)
            .contentType(mimeType)
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

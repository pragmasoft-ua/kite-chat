/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.domain;

import static jakarta.ws.rs.HttpMethod.GET;
import static jakarta.ws.rs.HttpMethod.PUT;
import static ua.com.pragmasoft.k1te.server.standalone.application.FileSystemStorageResource.STORAGE_API;

import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryMessage;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.BinaryPayload;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadRequest;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.UploadResponse;
import ua.com.pragmasoft.k1te.backend.ws.ObjectStore;
import ua.com.pragmasoft.k1te.server.standalone.infrastructure.JwtVerifier;

@ApplicationScoped
@IfBuildProfile("standalone")
public class FileSystemObjectStore implements ObjectStore {

  private static final Logger log = LoggerFactory.getLogger(FileSystemObjectStore.class);

  private final FileSystem fileSystem;
  private final String destinationPath;
  private final String storageEndpoint;
  private final JwtVerifier jwtVerifier;

  public FileSystemObjectStore(
      FileSystem fileSystem,
      @ConfigProperty(name = "local.object.store.path", defaultValue = "storage")
          String destinationPath,
      @ConfigProperty(name = "base.url") String baseUrl,
      JwtVerifier jwtVerifier) {

    log.debug(
        "Destination path for files is {}", fileSystem.getPath(destinationPath).toAbsolutePath());
    this.fileSystem = fileSystem;
    this.destinationPath = destinationPath;
    this.storageEndpoint = baseUrl + STORAGE_API;
    this.jwtVerifier = jwtVerifier;
  }

  @Override
  public UploadResponse presign(UploadRequest uploadRequest, String channelName, String memberId) {
    String fileName = uploadRequest.fileName();
    String fileType = uploadRequest.fileType();
    long fileSize = uploadRequest.fileSize();

    JwtVerifier.FileData getFileData =
        new JwtVerifier.FileData(
            GET, fileName, fileSize, fileType, channelName, memberId, uploadRequest.created());
    JwtVerifier.FileData putFileData =
        new JwtVerifier.FileData(
            PUT, fileName, fileSize, fileType, channelName, memberId, uploadRequest.created());

    URI getUri = this.presignUri(jwtVerifier.generateToken(getFileData));
    URI putUri = this.presignUri(jwtVerifier.generateToken(putFileData));

    log.debug("Download URI: {}", getUri);
    log.debug("Upload URI: {}", putUri);
    return new UploadResponse(uploadRequest.messageId(), getUri, putUri);
  }

  @Override
  public BinaryPayload copyTransient(BinaryPayload payload, String channelName, String memberId) {
    String fileName = payload.fileName();
    String fileType = payload.fileType();
    long fileSize = payload.fileSize();

    Path workDir = this.getWorkDir(channelName, memberId, payload.created());
    this.uploadFile(payload.uri(), workDir, fileName);

    JwtVerifier.FileData fileData =
        new JwtVerifier.FileData(
            GET, fileName, fileSize, fileType, channelName, memberId, payload.created());
    URI getUri = this.presignUri(jwtVerifier.generateToken(fileData));
    log.debug("Download URI: {}", getUri);

    return new BinaryMessage(
        getUri, fileName, fileType, fileSize, payload.messageId(), payload.created());
  }

  private void uploadFile(URI inputUri, Path parent, String fileName) {
    try (InputStream inputStream = inputUri.toURL().openStream()) {
      uploadFile(inputStream, parent, fileName);
    } catch (IOException e) {
      log.warn("Couldn't read data from a given URI");
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public void uploadFile(InputStream inputStream, Path parent, String fileName) {
    try (inputStream) {
      Files.createDirectories(parent);
      Path filePath = fileSystem.getPath(parent.toString(), fileName);

      if (!Files.exists(filePath)) {
        Files.createFile(filePath);
      }

      Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);
      log.debug("Upload file {} to {}", fileName, parent);
    } catch (IOException e) {
      log.warn("Couldn't upload file {}", fileName);
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public Path getFullPath(String channelName, String memberId, Instant createdAt, String fileName) {
    return Path.of(getWorkDir(channelName, memberId, createdAt).toString(), fileName);
  }

  public Path getWorkDir(String channelName, String memberId, Instant createdAt) {
    return fileSystem.getPath(
        destinationPath,
        channelName,
        memberId,
        createdAt.atZone(ZoneId.systemDefault()).toLocalDate().toString());
  }

  private URI presignUri(String token) {
    return URI.create("%s?token=%s".formatted(storageEndpoint, token));
  }
}

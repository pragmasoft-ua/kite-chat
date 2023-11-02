/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.hackathon.storage;

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
import ua.com.pragmasoft.k1te.server.hackathon.util.JwtUtil;

@ApplicationScoped
@IfBuildProfile("hackathon")
public class LocalObjectStore implements ObjectStore {

  private static final Logger log = LoggerFactory.getLogger(LocalObjectStore.class);

  public static final String GET = "GET";
  public static final String PUT = "PUT";

  private final FileSystem fileSystem;
  private final String destinationPath;
  private final String storageEndpoint;
  private final JwtUtil jwtUtil;

  public LocalObjectStore(
      FileSystem fileSystem,
      @ConfigProperty(name = "local.object.store.path") String destinationPath,
      @ConfigProperty(name = "local.object.store.endpoint") String storageEndpoint,
      JwtUtil jwtUtil) {

    if (!Files.exists(fileSystem.getPath(destinationPath))
        || !Files.isDirectory(fileSystem.getPath(destinationPath)))
      throw new IllegalStateException("Specified path for LocalObjectStore is invalid");

    this.fileSystem = fileSystem;
    this.destinationPath = destinationPath;
    this.storageEndpoint = storageEndpoint;
    this.jwtUtil = jwtUtil;
  }

  @Override
  public UploadResponse presign(UploadRequest uploadRequest, String channelName, String memberId) {
    String fileName = uploadRequest.fileName();
    String fileType = uploadRequest.fileType();
    long fileSize = uploadRequest.fileSize();

    JwtUtil.FileData getFileData =
        new JwtUtil.FileData(
            GET, fileName, fileSize, fileType, channelName, memberId, uploadRequest.created());
    JwtUtil.FileData putFileData =
        new JwtUtil.FileData(
            PUT, fileName, fileSize, fileType, channelName, memberId, uploadRequest.created());

    URI getUri = presignUri(jwtUtil.generateToken(getFileData));
    URI putUri = presignUri(jwtUtil.generateToken(putFileData));

    log.debug("Download URI: {}", getUri);
    log.debug("Upload URI: {}", putUri);
    return new UploadResponse(uploadRequest.messageId(), getUri, putUri);
  }

  @Override
  public BinaryPayload copyTransient(BinaryPayload payload, String channelName, String memberId) {
    String fileName = payload.fileName();
    String fileType = payload.fileType();
    long fileSize = payload.fileSize();

    Path workDir = workDir(channelName, memberId, payload.created());
    uploadFile(payload.uri(), workDir, fileName);

    JwtUtil.FileData fileData =
        new JwtUtil.FileData(
            GET, fileName, fileSize, fileType, channelName, memberId, payload.created());
    URI getUri = presignUri(jwtUtil.generateToken(fileData));
    log.debug("Download URI: {}", getUri);

    return new BinaryMessage(
        getUri, fileName, fileType, fileSize, payload.messageId(), payload.created());
  }

  private void uploadFile(URI inputUri, Path parent, String fileName) {
    try {
      uploadFile(inputUri.toURL().openStream(), parent, fileName);
    } catch (IOException e) {
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
    return Path.of(workDir(channelName, memberId, createdAt).toString(), fileName);
  }

  public Path workDir(String channelName, String memberId, Instant createdAt) {
    String dirPath =
        String.format("%s/%s/%tF", channelName, memberId, createdAt.atZone(ZoneId.systemDefault()));
    return fileSystem.getPath(destinationPath, dirPath);
  }

  private URI presignUri(String token) {
    return URI.create("%s?token=%s".formatted(storageEndpoint, token));
  }
}

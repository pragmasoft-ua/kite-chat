/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.application;

import com.auth0.jwt.exceptions.JWTVerificationException;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.file.Path;
import org.jboss.resteasy.reactive.Cache;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;
import ua.com.pragmasoft.k1te.server.standalone.domain.FileSystemObjectStore;
import ua.com.pragmasoft.k1te.server.standalone.infrastructure.JwtVerifier;

@ApplicationScoped
@IfBuildProfile("standalone")
@jakarta.ws.rs.Path(FileSystemStorageResource.STORAGE_API)
public class FileSystemStorageResource {

  private static final Logger log = LoggerFactory.getLogger(FileSystemStorageResource.class);
  public static final String STORAGE_API = "/api/storage";

  private final FileSystemObjectStore fileSystemObjectStore;
  private final JwtVerifier jwtVerifier;

  public FileSystemStorageResource(
      FileSystemObjectStore fileSystemObjectStore, JwtVerifier jwtVerifier) {
    this.fileSystemObjectStore = fileSystemObjectStore;
    this.jwtVerifier = jwtVerifier;
  }

  @GET
  @Cache(maxAge = 31536000)
  public RestResponse<Path> download(@RestQuery String token) {
    JwtVerifier.FileData fileData = jwtVerifier.validateAndDecodeToken(token, HttpMethod.GET);
    if (!fileData.method().equals(HttpMethod.GET))
      throw new ValidationException("You don't have permission to this resource");

    Path file =
        fileSystemObjectStore.getFullPath(
            fileData.channelName(), fileData.memberId(), fileData.createdAt(), fileData.fileName());

    log.debug("File {} is requested", fileData.fileName());
    return RestResponse.ResponseBuilder.ok(file, fileData.fileType())
        .header(HttpHeaders.CONTENT_LENGTH, fileData.fileSize())
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=\"%s\"".formatted(fileData.fileName()))
        .build();
  }

  @PUT
  public RestResponse<String> upload(InputStream inputStream, @RestQuery String token) {
    JwtVerifier.FileData fileData = jwtVerifier.validateAndDecodeToken(token, HttpMethod.PUT);
    if (!fileData.method().equals(HttpMethod.PUT))
      throw new ValidationException("You don't have permission to this resource");

    Path workDir =
        fileSystemObjectStore.getWorkDir(
            fileData.channelName(), fileData.memberId(), fileData.createdAt());

    fileSystemObjectStore.uploadFile(inputStream, workDir, fileData.fileName());
    return RestResponse.ok();
  }

  @ServerExceptionMapper
  public RestResponse<String> handleValidationException(ValidationException validationException) {
    return RestResponse.status(Response.Status.BAD_REQUEST, validationException.getMessage());
  }

  @ServerExceptionMapper(JWTVerificationException.class)
  public RestResponse<String> handleJWTVerificationException(
      JWTVerificationException jwtVerificationException) {
    return RestResponse.status(Response.Status.FORBIDDEN, jwtVerificationException.getMessage());
  }
}

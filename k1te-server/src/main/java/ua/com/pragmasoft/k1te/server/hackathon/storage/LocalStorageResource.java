/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.hackathon.storage;

import com.auth0.jwt.exceptions.JWTVerificationException;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import java.io.InputStream;
import java.nio.file.Path;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;
import ua.com.pragmasoft.k1te.server.hackathon.util.JwtUtil;

@ApplicationScoped
@IfBuildProfile("hackathon")
@jakarta.ws.rs.Path(LocalStorageResource.STORAGE_API)
public class LocalStorageResource {

  private static final Logger log = LoggerFactory.getLogger(LocalStorageResource.class);
  public static final String STORAGE_API = "/api/storage";

  private final LocalObjectStore localObjectStore;
  private final JwtUtil jwtUtil;

  public LocalStorageResource(LocalObjectStore localObjectStore, JwtUtil jwtUtil) {
    this.localObjectStore = localObjectStore;
    this.jwtUtil = jwtUtil;
  }

  @GET
  public RestResponse<Path> download(@RestQuery String token) {
    JwtUtil.FileData fileData = jwtUtil.validateToken(token, LocalObjectStore.GET);

    Path file =
        localObjectStore.getFullPath(
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
    JwtUtil.FileData fileData = jwtUtil.validateToken(token, LocalObjectStore.PUT);
    Path workDir =
        localObjectStore.workDir(fileData.channelName(), fileData.memberId(), fileData.createdAt());

    localObjectStore.uploadFile(inputStream, workDir, fileData.fileName());
    return RestResponse.ok();
  }

  @ServerExceptionMapper
  public RestResponse<String> handleValidationException(ValidationException validationException) {
    return RestResponse.status(Response.Status.BAD_REQUEST, validationException.getMessage());
  }

  @ServerExceptionMapper
  public RestResponse<String> handleJWTVerificationException(
      JWTVerificationException jwtVerificationException) {
    return RestResponse.status(Response.Status.FORBIDDEN, jwtVerificationException.getMessage());
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.infrastructure;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import io.quarkus.arc.profile.IfBuildProfile;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.time.ZonedDateTime;
import ua.com.pragmasoft.k1te.backend.shared.ValidationException;
import ua.com.pragmasoft.k1te.server.standalone.application.JwtProperties;

@ApplicationScoped
@IfBuildProfile("standalone")
public class JwtVerifier {

  private static final String FILENAME = "fileName";
  private static final String FILETYPE = "fileType";
  private static final String FILESIZE = "fileSize";
  private static final String METHOD = "method";
  private static final String CHANNEL_NAME = "channelName";

  private final JwtProperties jwtProperties;

  public JwtVerifier(JwtProperties jwtProperties) {
    this.jwtProperties = jwtProperties;
  }

  public String generateToken(FileData fileData) {
    return JWT.create()
        .withSubject(fileData.memberId)
        .withClaim(METHOD, fileData.method)
        .withClaim(FILENAME, fileData.fileName)
        .withClaim(FILETYPE, fileData.fileType)
        .withClaim(FILESIZE, fileData.fileSize)
        .withClaim(CHANNEL_NAME, fileData.channelName)
        .withIssuer(jwtProperties.issuer())
        .withIssuedAt(fileData.createdAt)
        .withExpiresAt(ZonedDateTime.now().plusMinutes(jwtProperties.tokenDuration()).toInstant())
        .sign(Algorithm.HMAC256(jwtProperties.secret()));
  }

  public FileData validateAndDecodeToken(String token, String requiredMethod) {
    try {
      JWTVerifier verifier =
          JWT.require(Algorithm.HMAC256(jwtProperties.secret()))
              .withClaim(METHOD, requiredMethod)
              .withIssuer(jwtProperties.issuer())
              .build();

      DecodedJWT decodedJWT = verifier.verify(token);

      String method = decodedJWT.getClaim(METHOD).asString();
      String fileName = decodedJWT.getClaim(FILENAME).asString();
      Long fileSize = decodedJWT.getClaim(FILESIZE).asLong();
      String fileType = decodedJWT.getClaim(FILETYPE).asString();
      String channelName = decodedJWT.getClaim(CHANNEL_NAME).asString();
      String memberId = decodedJWT.getSubject();
      Instant createdAt = decodedJWT.getIssuedAt().toInstant();

      return new FileData(method, fileName, fileSize, fileType, channelName, memberId, createdAt);
    } catch (JWTVerificationException jwtException) {
      throw new ValidationException(jwtException.getMessage(), jwtException);
    }
  }

  public record FileData(
      String method,
      String fileName,
      Long fileSize,
      String fileType,
      String channelName,
      String memberId,
      Instant createdAt) {}
}

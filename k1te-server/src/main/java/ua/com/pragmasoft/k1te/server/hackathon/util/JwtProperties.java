/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.hackathon.util;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.config.ConfigMapping;

@IfBuildProfile("hackathon")
@ConfigMapping(prefix = "local.object.store.jwt")
public interface JwtProperties {
  String issuer();

  Long tokenDuration();

  String secret();
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.standalone.application;

import io.quarkus.arc.profile.IfBuildProfile;
import io.smallrye.config.ConfigMapping;

@IfBuildProfile("standalone")
@ConfigMapping(prefix = "local.object.store.jwt")
public interface JwtProperties {
  String issuer();

  /** The value should be specified in Minutes */
  Long tokenDuration();

  String secret();
}

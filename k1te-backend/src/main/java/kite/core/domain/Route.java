/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.util.Objects;

public record Route(RoutingProvider.Id provider, String raw) {

  public Route {
    Objects.requireNonNull(provider, "provider");
    Objects.requireNonNull(raw, "raw");
  }

  @Override
  public String toString() {
    return this.provider.raw() + ':' + this.raw;
  }

  public static Route of(String s) {
    var parts = s.split(":", 2);
    Objects.checkIndex(1, parts.length);
    return new Route(RoutingProvider.Id.of(parts[0]), parts[1]);
  }
}

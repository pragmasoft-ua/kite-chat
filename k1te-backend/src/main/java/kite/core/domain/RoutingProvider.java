/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.util.Optional;
import kite.core.domain.exception.KiteException;
import kite.core.domain.payload.Payload;

public interface RoutingProvider {

  Id id();

  Optional<Payload> send(Route route, Payload message) throws KiteException;

  record Id(String raw) {

    public Id {
      assert null != raw && !raw.isBlank() : "raw";
    }

    @Override
    public String toString() {
      return this.raw;
    }

    public static Id of(String raw) {
      return new Id(raw);
    }
  }
}

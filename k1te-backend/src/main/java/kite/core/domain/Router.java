/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import kite.core.domain.exception.KiteException;
import kite.core.domain.exception.RoutingException;
import kite.core.domain.payload.Payload;

public class Router implements RoutingProvider {

  public static final Id ROUTER_ID = Id.of("router");

  final Map<Id, RoutingProvider> providers;

  @Override
  public Id id() {
    return ROUTER_ID;
  }

  Router(RoutingProvider... providers) {
    this.providers =
        Arrays.stream(providers)
            .collect(Collectors.toMap(RoutingProvider::id, Function.identity()));
    assert this.providers.size() > 1 : "router expects more than one routing provider";
  }

  @Override
  public Optional<Payload> send(Route route, Payload message) throws KiteException {
    Id providerId = route.provider();
    var provider = providers.get(providerId);
    if (provider == null) {
      throw new RoutingException("Unsupported route provider: " + providerId);
    }
    return provider.send(route, message);
  }

  @Override
  public String toString() {
    return this.id() + " -> " + this.providers.keySet().toString();
  }
}

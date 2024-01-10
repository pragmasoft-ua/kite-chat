/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@RecordBuilder
public record Member(Id id, String userName, Set<Route> routes) implements MemberBuilder.With {

  private static final String REQUIRED = " is required";

  public record Id(String channelName, String raw) {
    public Id {
      assert null != channelName : "channelName" + REQUIRED;
      assert null != raw : "raw" + REQUIRED;
    }

    @Override
    public String toString() {
      return channelName + '#' + raw;
    }
  }

  public Member {
    assert null != id : "id" + REQUIRED;
    assert null != userName : "userName" + REQUIRED;
    routes = Objects.requireNonNullElse(routes, Set.of());
    routes = Set.copyOf(routes);
  }

  public Optional<Route> routeTo(RoutingProvider.Id provider) {
    return this.routes().stream().filter(route -> route.provider().equals(provider)).findAny();
  }

  public Member withoutRoute(Route route) {
    if (!routes().contains(route)) {
      return this;
    }
    var mutableRoutes = new HashSet<>(routes());
    mutableRoutes.remove(route);
    return withRoutes(mutableRoutes);
  }

  public Member withRoute(Route route) {
    if (routes().contains(route)) {
      return this;
    }
    var mutableRoutes = new HashSet<>(routes());
    var oldRouteToSameProvider = routeTo(route.provider());
    if (oldRouteToSameProvider.isPresent()) {
      mutableRoutes.remove(oldRouteToSameProvider.get());
    }
    mutableRoutes.add(route);
    return withRoutes(mutableRoutes);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!(obj instanceof Member)) return false;
    Member other = (Member) obj;
    return Objects.equals(id, other.id);
  }
}

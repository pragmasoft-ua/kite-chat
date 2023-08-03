package ua.com.pragmasoft.k1te.router.domain;

import java.util.Objects;
import ua.com.pragmasoft.k1te.shared.KiteException;

public interface Connector {

  String id();

  void dispatch(RoutingContext context) throws KiteException;

  default String connectionUri(String rawConnection) {
    return id() + ':' + rawConnection;
  }

  default String rawConnection(String connectionUri) {
    var array = connectionUri.split(":", 2);
    Objects.checkIndex(1, array.length);
    return array[1];
  }

  default String connectorId(String connectionUri) {
    var array = connectionUri.split(":", 2);
    return array[0];
  }
}

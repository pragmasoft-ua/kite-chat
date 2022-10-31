package ua.com.pragmasoft.k1te.chat;

import java.net.URI;

public record Route(ConnectorId connectorId, String connectorSpecificDestination) {
  public Route(URI uri) {
    this(new ConnectorId(uri.getScheme()), uri.getSchemeSpecificPart());
  }

  public URI uri() {
    return URI.create(connectorId.raw() + ':' + connectorSpecificDestination);
  }
}

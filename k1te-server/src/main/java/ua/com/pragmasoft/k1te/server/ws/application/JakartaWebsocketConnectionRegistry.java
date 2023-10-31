/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.server.ws.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.EncodeException;
import jakarta.websocket.Session;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector.WsConnection;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector.WsConnectionRegistry;

@ApplicationScoped
public class JakartaWebsocketConnectionRegistry implements WsConnectionRegistry {

  private final Map<String, JakartaWebsocketConnection> connections = new ConcurrentHashMap<>();

  void unregisterConnection(JakartaWebsocketConnection connection) {
    var done = this.connections.remove(connection.connectionUri());
    assert null != done : "Already unregistered connection";
  }

  JakartaWebsocketConnection createConnection(Session session) {
    return new JakartaWebsocketConnection(session);
  }

  void registerConnection(JakartaWebsocketConnection connection) {

    var existing = this.connections.putIfAbsent(connection.connectionUri(), connection);
    assert null == existing : "Already registered connection";
  }

  @Override
  public WsConnection getConnection(String connectionUri) {
    return this.connections.get(connectionUri);
  }

  class JakartaWebsocketConnection implements WsConnection {

    private final Session session;

    /**
     * @param session
     */
    JakartaWebsocketConnection(Session session) {
      this.session = session;
    }

    @Override
    public void close() throws IOException {
      JakartaWebsocketConnectionRegistry.this.unregisterConnection(this);
      this.session.close(new CloseReason(CloseCodes.NORMAL_CLOSURE, "Forcibly closed"));
    }

    @Override
    public String connectionUri() {
      return this.session.getId();
    }

    @Override
    public void sendObject(Payload payload) throws IOException {
      try {
        this.session.getBasicRemote().sendObject(payload);
      } catch (EncodeException e) {
        throw new IOException(e.getMessage(), e);
      }
    }
  }
}

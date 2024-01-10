/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package ua.com.pragmasoft.k1te.server.ws.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.List;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.shared.OnWsConnectionFailedException;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.server.ws.application.JakartaWebsocketConnectionRegistry.JakartaWebsocketConnection;

@ServerEndpoint(
    value = JakartaWebsocketAdapter.CHANNELS_PATH,
    decoders = {PayloadDecoderAdapter.class},
    encoders = {PayloadEncoderAdapter.class},
    subprotocols = {WsConnector.SUBPROTOCOL})
@ApplicationScoped
public class JakartaWebsocketAdapter {

  public static final String CHANNELS_PATH = "/channels";

  private final JakartaWebsocketConnectionRegistry connectionRegistry;

  private final WsConnector wsConnector;

  @Inject
  public JakartaWebsocketAdapter(
      WsConnector wsConnector, JakartaWebsocketConnectionRegistry connectionRegistry) {
    this.wsConnector = wsConnector;
    this.connectionRegistry = connectionRegistry;
  }

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) throws IOException {
    var timeout = 3; // 3x client side ping interval
    session.setMaxIdleTimeout(timeout * 60L * 1000L);
    JakartaWebsocketConnection connection = this.connectionRegistry.createConnection(session);
    this.connectionRegistry.registerConnection(connection);
    try {
      String channelName = this.getParameter(session, "c");
      String memberId = this.getParameter(session, "m");
      var response = wsConnector.onOpen(connection, channelName, memberId);
      if (null != response) {
        connection.sendObject(response);
      }
    } catch (OnWsConnectionFailedException e) {
      session.close(new CloseReason(() -> 1007, e.getMessage())); // 1007 - Unsupported payload
    }
  }

  @OnClose
  public void onClose(Session session, CloseReason closeReason) throws IOException {
    var connection = this.connectionRegistry.createConnection(session);
    try {
      var response = this.wsConnector.onClose(connection);
      if (null != response) {
        connection.sendObject(response);
      }
    } finally {
      this.connectionRegistry.unregisterConnection(connection);
    }
  }

  @OnError
  public void onError(Session session, Throwable t) throws IOException {
    var connection = this.connectionRegistry.createConnection(session);
    var response = this.wsConnector.onError(connection, t);
    if (null != response) {
      connection.sendObject(response);
    }
  }

  @OnMessage
  public void onPayload(Payload payload, Session session) throws IOException {
    var connection = this.connectionRegistry.createConnection(session);
    var response = this.wsConnector.onPayload(payload, connection);
    if (null != response) {
      connection.sendObject(response);
    }
  }

  private String getParameter(Session session, String name) {
    List<String> values = session.getRequestParameterMap().get(name);
    return values != null && !values.isEmpty() ? values.get(0) : null;
  }
}

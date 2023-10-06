package ua.com.pragmasoft.k1te.server.ws.application;

import java.io.IOException;

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
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.server.ws.application.JakartaWebsocketConnectionRegistry.JakartaWebsocketConnection;

@ServerEndpoint(value = JakartaWebsocketAdapter.CHANNELS_PATH, decoders = {
    PayloadDecoderAdapter.class }, encoders = {
        PayloadEncoderAdapter.class }, subprotocols = { WsConnector.SUBPROTOCOL })
@ApplicationScoped
public class JakartaWebsocketAdapter {

  public static final String CHANNELS_PATH = "/channels";

  private final JakartaWebsocketConnectionRegistry connectionRegistry;

  private final WsConnector wsConnector;

  @Inject
  public JakartaWebsocketAdapter(WsConnector wsConnector, JakartaWebsocketConnectionRegistry connectionRegistry) {
    this.wsConnector = wsConnector;
    this.connectionRegistry = connectionRegistry;
  }

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) throws IOException {
    session.setMaxIdleTimeout(60L * 1000L);
    JakartaWebsocketConnection connection = this.connectionRegistry.createConnection(session);
    this.connectionRegistry.registerConnection(connection);
    var response = wsConnector.onOpen(connection);
    if (null != response) {
      connection.sendObject(response);
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

}

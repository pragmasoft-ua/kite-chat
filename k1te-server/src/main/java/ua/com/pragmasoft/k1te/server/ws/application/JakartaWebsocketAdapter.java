package ua.com.pragmasoft.k1te.server.ws.application;

import java.io.IOException;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.websocket.CloseReason;
import jakarta.websocket.EncodeException;
import jakarta.websocket.EndpointConfig;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.Payload;
import ua.com.pragmasoft.k1te.backend.ws.WsConnector;
import ua.com.pragmasoft.k1te.server.ws.application.JakartaWebsocketConnectionRegistry.JakartaWebsocketConnection;

@ServerEndpoint(value = JakartaWebsocketAdapter.CHANNELS_PATH + "{channelName}", decoders = {
    PayloadDecoderAdapter.class }, encoders = {
        PayloadEncoderAdapter.class }, subprotocols = { WsConnector.SUBPROTOCOL })
@ApplicationScoped
public class JakartaWebsocketAdapter {

  public static final String CHANNELS_PATH = "/channels/";

  private final JakartaWebsocketConnectionRegistry connectionRegistry;

  private final WsConnector wsConnector;

  @Inject
  public JakartaWebsocketAdapter(WsConnector wsConnector, JakartaWebsocketConnectionRegistry connectionRegistry) {
    this.wsConnector = wsConnector;
    this.connectionRegistry = connectionRegistry;
  }

  @OnOpen
  public void onOpen(Session session, EndpointConfig config) {
    session.setMaxIdleTimeout(60L * 1000L);
    JakartaWebsocketConnection connection = this.connectionRegistry.createConnection(session);
    this.connectionRegistry.registerConnection(connection);
    wsConnector.onOpen(connection);
  }

  @OnClose
  public void onClose(Session session, @PathParam("channelName") String channelName, CloseReason closeReason) {
    var connection = this.connectionRegistry.createConnection(session);
    try {
      this.wsConnector.onClose(connection);
    } finally {
      this.connectionRegistry.unregisterConnection(connection);
    }
  }

  @OnError
  public void onError(Session session, Throwable t) {
    var connection = this.connectionRegistry.createConnection(session);
    this.wsConnector.onError(connection, t);
  }

  @OnMessage
  public void onPayload(Payload payload, @PathParam("channelName") String channelName,
      Session session) throws IOException, EncodeException {
    var connection = this.connectionRegistry.createConnection(session);
    this.wsConnector.onPayload(payload, connection, channelName);
  }

}

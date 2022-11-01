package ua.com.pragmasoft.k1te.ws.payload;

public sealed
interface KiteMsg
permits ConnectedMsg, DisconnectedMsg, ErrorMsg, PlaintextMsg
{

  /**
   * Mesage type constants. Should be consecutive as they're used as indices in the list of
   * serializers/deserializers
   */
  static final short CONNECTED = 0;
  static final short DISCONNECTED = 1;
  static final short ERROR = 2;
  static final short PLAINTEXT = 3;

  short type();
}

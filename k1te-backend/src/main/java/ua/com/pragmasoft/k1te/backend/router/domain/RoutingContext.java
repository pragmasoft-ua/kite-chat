/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.domain;

import java.util.HashMap;
import java.util.Map;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessageAck;
import ua.com.pragmasoft.k1te.backend.router.domain.payload.MessagePayload;

public final class RoutingContext {

  public String originConnection;
  public String destinationConnection;
  public Member from;
  public Member to;
  public MessagePayload request;
  public MessageAck response;
  Map<String, Object> attributes;

  public Map<String, Object> attributes() {
    if (null == this.attributes) {
      this.attributes = new HashMap<>();
    }
    return this.attributes;
  }

  public static RoutingContext create() {
    return new RoutingContext();
  }

  public RoutingContext withOriginConnection(String originConnection) {
    this.originConnection = originConnection;
    return this;
  }

  public RoutingContext withDestinationConnection(String destinationConnection) {
    this.destinationConnection = destinationConnection;
    return this;
  }

  public RoutingContext withFrom(Member from) {
    this.from = from;
    return this;
  }

  public RoutingContext withTo(Member to) {
    this.to = to;
    return this;
  }

  public RoutingContext withRequest(MessagePayload request) {
    this.request = request;
    return this;
  }

  public RoutingContext withResponse(MessageAck response) {
    this.response = response;
    return this;
  }

  public RoutingContext withAttribute(String name, Object value) {
    this.attributes().put(name, value);
    return this;
  }

  public Object attribute(String name) {
    return (null != this.attributes) ? this.attributes.get(name) : null;
  }

  @Override
  public String toString() {
    return "RoutingContext [originConnection="
        + originConnection
        + ", destinationConnection="
        + destinationConnection
        + ", from="
        + from
        + ", to="
        + to
        + ", request="
        + request
        + ", response="
        + response
        + ", attributes="
        + attributes
        + "]";
  }
}

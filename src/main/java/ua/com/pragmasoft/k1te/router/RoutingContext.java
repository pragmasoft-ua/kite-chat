package ua.com.pragmasoft.k1te.router;

public final class RoutingContext {

  final RoutingRequest routingRequest;
  RoutingResponse routingResponse;
  Chat chat;
  Conversation conversation;
  Route destination;

  /**
   * @param routingRequest
   */
  public RoutingContext(RoutingRequest routingRequest) {
    this.routingRequest = routingRequest;
  }

  /**
   * @return the routingRequest
   */
  public RoutingRequest getRoutingRequest() {
    return routingRequest;
  }

  /**
   * @return the routingResponse
   */
  public RoutingResponse getRoutingResponse() {
    return routingResponse;
  }

  /**
   * @return the chat
   */
  public Chat getChat() {
    return chat;
  }

  /**
   * @return the conversation
   */
  public Conversation getConversation() {
    return conversation;
  }

  /**
   * @return the destination
   */
  public Route getDestination() {
    return destination;
  }

  @Override
  public String toString() {
    return "RoutingContext [origin=" + routingRequest.origin().uri().toString() + ", destination="
        + destination.uri().toString() + "]";
  }

  public RoutingContext withResponse(RoutingResponse r) {
    this.routingResponse = r;
    return this;
  }

}

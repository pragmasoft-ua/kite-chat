package ua.com.pragmasoft.k1te.shared;

public class RoutingException extends KiteException {

  private static final long serialVersionUID = 1L;
  private static final int NO_ROUTE = 502;

  /**
   * @param message
   */
  public RoutingException() {
    this("Cannot dispatch request, no route found");
  }

  /**
   * @param message
   */
  public RoutingException(String message) {
    super(message);
  }

  /**
   * @param message
   * @param cause
   */
  public RoutingException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public int code() {
    return NO_ROUTE;
  }



}

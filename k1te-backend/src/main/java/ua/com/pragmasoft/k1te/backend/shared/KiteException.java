package ua.com.pragmasoft.k1te.backend.shared;

public class KiteException extends RuntimeException {

  private static final long serialVersionUID = 1L;
  public static final int SERVER_ERROR = 500;

  /**
   * @param message
   */
  public KiteException(String message) {
    super(message);
  }

  /**
   * @param message
   * @param cause
   */
  public KiteException(String message, Throwable cause) {
    super(message, cause);
  }

  public int code() {
    return SERVER_ERROR;
  }

}

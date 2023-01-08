package ua.com.pragmasoft.k1te.shared;

public class ConflictException extends KiteException {

  private static final long serialVersionUID = 1L;
  public static int CONFLICT_ERROR = 409;

  /**
   * @param message
   */
  public ConflictException(String message) {
    super(message);
  }

  /**
   * @param message
   * @param cause
   */
  public ConflictException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public int code() {
    return CONFLICT_ERROR;
  }



}

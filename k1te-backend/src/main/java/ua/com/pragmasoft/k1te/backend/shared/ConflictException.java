/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.shared;

public class ConflictException extends KiteException {

  private static final long serialVersionUID = 1L;
  public static final int CONFLICT_ERROR = 409;

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

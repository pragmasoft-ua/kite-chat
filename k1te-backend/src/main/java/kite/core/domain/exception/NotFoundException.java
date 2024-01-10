/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.exception;

public class NotFoundException extends KiteException {

  private static final long serialVersionUID = 1L;
  private static final int NOT_FOUND = 404;

  /**
   * @param message
   */
  public NotFoundException(String message) {
    super(message);
  }

  public NotFoundException() {
    this("Not found");
  }

  /**
   * @param message
   * @param cause
   */
  public NotFoundException(String message, Throwable cause) {
    super(message, cause);
  }

  @Override
  public int code() {
    return NOT_FOUND;
  }
}

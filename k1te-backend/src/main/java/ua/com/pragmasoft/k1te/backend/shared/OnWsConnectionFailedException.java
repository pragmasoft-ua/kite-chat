/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.shared;

public class OnWsConnectionFailedException extends RuntimeException {
  private Integer status;

  public OnWsConnectionFailedException(String message) {
    super(message);
  }

  public OnWsConnectionFailedException(Exception e) {
    super(e.getMessage());
    switch (e) {
      case ValidationException validationException -> this.status = 400;
      case NotFoundException notFoundException -> this.status = 404;
      case IllegalStateException illegalStateException -> this.status = 400;
      case KiteException kiteException -> this.status = 500;
      default -> {}
    }
  }

  public Integer getStatus() {
    return status;
  }
}

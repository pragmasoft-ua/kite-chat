/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import kite.core.domain.exception.KiteException;

public record Error(String reason, int code) implements Payload {

  public static Error err(String reason) {
    return new Error(reason, KiteException.SERVER_ERROR);
  }

  public static Error err(String reason, int code) {
    return new Error(reason, code);
  }

  public static Error err(KiteException cause) {
    return new Error(cause.getMessage(), cause.code());
  }

  @Override
  public String toString() {
    return Severity.ERR.label + '(' + this.code + ") " + this.reason;
  }
}

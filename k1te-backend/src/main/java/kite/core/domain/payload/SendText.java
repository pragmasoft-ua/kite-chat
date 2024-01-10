/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

public non-sealed interface SendText extends MessagePayload {

  public enum Mode {
    NEW,
    EDITED
  }

  String text();

  Mode mode();

  SendText overrideText(String text);
}

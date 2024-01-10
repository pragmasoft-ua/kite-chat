/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

public record Notification(String text, Severity severity) implements Payload {

  public static Notification info(String text) {
    return new Notification(text, Severity.OK);
  }

  public static Notification warn(String text) {
    return new Notification(text, Severity.WARN);
  }

  public static Notification plain(String text) {
    return new Notification(text, Severity.NONE);
  }

  @Override
  public String toString() {
    return this.severity.label + this.text;
  }
}

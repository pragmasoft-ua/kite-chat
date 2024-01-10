/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

public enum Severity {
  ERR("⛔ "),
  WARN("⚠️ "),
  OK("✅ "),
  NONE("");

  final String label;

  Severity(String label) {
    this.label = label;
  }
}

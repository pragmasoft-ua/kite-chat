/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class MemberTest {
  @Test
  void testAssertionError() {
    assertThrows(
        AssertionError.class,
        MemberBuilder.builder()::build,
        "Should throw AssertionError on missing id");
  }
}

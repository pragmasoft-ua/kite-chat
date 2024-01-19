/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import static org.testcontainers.shaded.org.hamcrest.MatcherAssert.assertThat;
import static org.testcontainers.shaded.org.hamcrest.Matchers.greaterThan;

import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RandomIdTest {

  static final Logger log = LoggerFactory.getLogger(RandomIdTest.class);

  static Random rnd = new Random();

  public static String randomId() {
    return Long.toHexString(rnd.nextLong());
  }

  @Test
  @DisplayName("Should return hex string 16 characters long")
  void testGenerateRandomId() {
    var s = randomId();
    log.debug(s);
    assertThat(s.length(), greaterThan(10));
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import java.time.Instant;

public non-sealed interface Ack extends Payload {

  String messageId();

  String overrideMessageId();

  Instant timestamp();
}

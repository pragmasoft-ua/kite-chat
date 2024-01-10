/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import java.time.Instant;

public sealed interface MessagePayload extends Payload permits SendText, DeleteMessage {

  String messageId();

  Instant timestamp();
}

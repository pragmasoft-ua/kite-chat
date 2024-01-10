/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.application.telegram;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;
import kite.core.domain.payload.Ack;

@RecordBuilder
public record TgAckRecord(
    String overrideMessageId, String messageId, Integer tgMessageId, Instant timestamp)
    implements Ack {}

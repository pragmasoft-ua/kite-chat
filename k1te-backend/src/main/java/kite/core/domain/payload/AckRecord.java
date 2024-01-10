/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.time.Instant;

@RecordBuilder
public record AckRecord(String overrideMessageId, String messageId, Instant timestamp)
    implements Ack {}

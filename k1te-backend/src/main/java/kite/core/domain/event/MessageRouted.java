/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.event;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.util.Map;
import kite.core.domain.Channel;
import kite.core.domain.Direction;
import kite.core.domain.Member;
import kite.core.domain.Route;
import kite.core.domain.payload.MessagePayload;
import kite.core.domain.payload.Payload;

@RecordBuilder
public record MessageRouted(
    Channel channel,
    Member member,
    Direction direction,
    MessagePayload request,
    Map<Route, Payload> responses)
    implements Event {}

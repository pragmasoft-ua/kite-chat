/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain.payload;

import java.io.Serializable;

public sealed interface Payload extends Serializable
    permits MessagePayload, Ack, Notification, Error {}

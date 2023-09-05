package ua.com.pragmasoft.k1te.backend.router.domain.payload;

public sealed interface MessagePayload extends Payload permits PlaintextMessage {

  String messageId();

}

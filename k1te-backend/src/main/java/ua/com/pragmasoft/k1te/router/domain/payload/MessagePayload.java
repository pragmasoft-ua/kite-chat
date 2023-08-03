package ua.com.pragmasoft.k1te.router.domain.payload;

public sealed interface MessagePayload extends Payload permits PlaintextMessage {

  String messageId();

}

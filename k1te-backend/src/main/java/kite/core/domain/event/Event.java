/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain.event;

public sealed interface Event permits MessageRouted, ChannelEvent, MemberEvent {

  @FunctionalInterface
  public interface Publisher {
    void publishEvent(Event event);
  }
}

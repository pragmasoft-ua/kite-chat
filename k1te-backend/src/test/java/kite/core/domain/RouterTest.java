/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Optional;
import kite.core.domain.exception.RoutingException;
import kite.core.domain.payload.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouterTest {

  @Test
  @DisplayName(
      "Should throw AssertionException on constructor method if given less than 2 providers")
  void should_trow_assertion_exception_on_constructor_if_providers_less_than_2() {
    var provider1 = mock(RoutingProvider.class);
    doReturn(RoutingProvider.Id.of("tg")).when(provider1).id();

    assertThrows(AssertionError.class, () -> new Router(provider1));
  }

  @Test
  @DisplayName("should send payload to an appropriate provider found by its id")
  void should_send_payload_to_appropriate_provide_by_its_id() {
    Route route = Route.of("tg:someId");
    Notification payload = Notification.info("OK");
    Notification responsePayload = Notification.info("DONE");
    var provider1 = mock(RoutingProvider.class);
    var provider2 = mock(RoutingProvider.class);
    var provider3 = mock(RoutingProvider.class);

    doReturn(RoutingProvider.Id.of("tg")).when(provider1).id();
    doReturn(RoutingProvider.Id.of("ws")).when(provider2).id();
    doReturn(RoutingProvider.Id.of("ai")).when(provider3).id();
    doReturn(Optional.of(responsePayload)).when(provider1).send(route, payload);

    Router router = new Router(provider1, provider2, provider3);
    var response = router.send(route, payload);

    assertThat(response).isPresent().get().isSameAs(responsePayload);
    verify(provider1, times(1)).send(route, payload);
    verify(provider2, never()).send(any(), any());
    verify(provider3, never()).send(any(), any());
  }

  @Test
  @DisplayName("Should throw RoutingException if provider not found by its id")
  void should_throw_routing_exception_if_there_is_no_router_with_given_id() {
    Route route = Route.of("ai:someId");
    Notification payload = Notification.info("OK");
    var provider1 = mock(RoutingProvider.class);
    var provider2 = mock(RoutingProvider.class);

    doReturn(RoutingProvider.Id.of("tg")).when(provider1).id();
    doReturn(RoutingProvider.Id.of("ws")).when(provider2).id();

    Router router = new Router(provider1, provider2);
    var routingException = assertThrows(RoutingException.class, () -> router.send(route, payload));

    assertThat(routingException).hasMessage("Unsupported route provider: ai");
    verify(provider1, never()).send(any(), any());
    verify(provider2, never()).send(any(), any());
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

/**
 * Telegram cannot keep association between origin and destination message ids, so we need to keep
 * the association in the database, otherwise we'll be unable to edit and delete destination
 * messages.
 *
 * <p>Implementation of IdMapping should preferably have ttl to autoremove.
 */
public interface MessageIdMapper {

  public interface IdMapping {

    Member.Id from();

    RoutingProvider.Id destinationProvider();

    String originId();

    Member.Id to();

    String destinationId();
  }

  IdMapping find(Member.Id from, String originId, RoutingProvider.Id destinationProvider);

  IdMapping append(
      Member.Id from,
      Member.Id to,
      String originId,
      RoutingProvider.Id destinationProvider,
      String destinationId);

  IdMapping delete(Member.Id from, String originId, RoutingProvider.Id destinationProvider);
}

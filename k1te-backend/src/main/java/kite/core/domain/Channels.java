/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.domain;

public interface Channels {

  Channel get(String name);

  String getChannelName(String hostId);
}

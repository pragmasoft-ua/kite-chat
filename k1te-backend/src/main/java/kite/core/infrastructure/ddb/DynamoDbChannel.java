/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.infrastructure.ddb;

import java.util.Optional;
import kite.core.domain.Channel;
import kite.core.domain.ChannelBuilder;
import kite.core.domain.Member.Id;
import kite.core.domain.Route;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
class DynamoDbChannel implements Keyed {

  private String name;
  private String hostId;
  private String defaultRoute;
  private String chatBot;
  private String peerMember;

  DynamoDbChannel() {
    super();
  }

  DynamoDbChannel(Channel channel) {
    super();
    this.name = channel.name();
    this.hostId = channel.hostId();
    this.defaultRoute = channel.defaultRoute().toString();
    var chatbot = channel.chatBot();
    if (chatbot.isPresent()) {
      this.chatBot = chatbot.get().toString();
    }
    var peer = channel.peerMember();
    if (peer.isPresent()) {
      this.peerMember = peer.get().raw();
    }
  }

  @DynamoDbIgnore
  Channel toChannel() {
    return ChannelBuilder.builder()
        .name(this.name)
        .hostId(this.hostId)
        .defaultRoute(Route.of(this.defaultRoute))
        .chatBot(Optional.ofNullable(this.chatBot).map(Route::of))
        .peerMember(Optional.ofNullable(this.peerMember).map(raw -> new Id(this.name, raw)))
        .build();
  }

  @DynamoDbPartitionKey
  String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
  }

  String getHostId() {
    return hostId;
  }

  void setHostId(String hostId) {
    this.hostId = hostId;
  }

  String getDefaultRoute() {
    return defaultRoute;
  }

  void setDefaultRoute(String defaultRoute) {
    this.defaultRoute = defaultRoute;
  }

  String getChatBot() {
    return chatBot;
  }

  void setChatBot(String chatBot) {
    this.chatBot = chatBot;
  }

  String getPeerMember() {
    return peerMember;
  }

  void setPeerMember(String peerMember) {
    this.peerMember = peerMember;
  }

  @Override
  @DynamoDbIgnore
  public Key key() {
    return key(this.name);
  }

  @DynamoDbIgnore
  public static Key key(String name) {
    return Key.builder().partitionValue(name).build();
  }
}

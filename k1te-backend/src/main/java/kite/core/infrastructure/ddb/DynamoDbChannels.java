/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import java.util.Optional;
import kite.core.domain.Channel;
import kite.core.domain.Channels;
import kite.core.domain.event.ChannelEvent.ChannelCreated;
import kite.core.domain.event.ChannelEvent.ChannelDropped;
import kite.core.domain.event.ChannelEvent.ChannelUpdated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;

class DynamoDbChannels implements Channels {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbChannels.class);

  public static final String REVERSE_CHANNEL_KEY_PREFIX = "host:";

  private static Expression nameNotExistsCondition =
      Expression.builder()
          .expression("attribute_not_exists(#attr)")
          .putExpressionName("#attr", "name") // name is a dynamodb keyword
          .build();

  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbTable<DynamoDbChannel> channelsTable;
  private final DynamoDbTable<DynamoDbConnection> connectionsTable;

  public DynamoDbChannels(
      DynamoDbEnhancedClient enhancedDynamo,
      DynamoDbTable<DynamoDbChannel> channelsTable,
      DynamoDbTable<DynamoDbConnection> connectionsTable) {
    this.enhancedDynamo = enhancedDynamo;
    this.channelsTable = channelsTable;
    this.connectionsTable = connectionsTable;
  }

  @Override
  public Channel get(String channelName) {
    var item = this.channelsTable.getItem(DynamoDbChannel.key(channelName));
    return Optional.ofNullable(item).map(DynamoDbChannel::toChannel).orElse(null);
  }

  @Override
  public String getChannelName(String hostId) {
    var item = this.channelsTable.getItem(DynamoDbChannel.key(REVERSE_CHANNEL_KEY_PREFIX + hostId));
    return Optional.ofNullable(item).map(DynamoDbChannel::getHostId).orElse(null);
  }

  void put(Channel channel) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'put'");
  }

  void delete(String channelName) {
    // delete members
    // delete connections
    // delete history
    // delete unanswered
    throw new UnsupportedOperationException("Unimplemented method 'delete'");
  }

  public void onChannelCreated(ChannelCreated event) {
    var channel = event.channel();
    this.put(channel);
  }

  public void onChannelUpdated(ChannelUpdated event) {
    // var channel = event.channel();
    // this.put(channel);
    throw new UnsupportedOperationException("Unimplemented method 'delete'");
  }

  public void onChannelDropped(ChannelDropped event) {
    var channelName = event.channelName();
    this.delete(channelName);
  }
}

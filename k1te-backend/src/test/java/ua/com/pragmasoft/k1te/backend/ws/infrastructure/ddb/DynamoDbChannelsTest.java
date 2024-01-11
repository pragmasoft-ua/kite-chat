/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.backend.ws.infrastructure.ddb;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import ua.com.pragmasoft.k1te.backend.router.infrastructure.DynamoDbChannels;

class DynamoDbChannelsTest extends DynamoDbBaseTest {

  private static DynamoDbChannels dynamoDbChannels;

  @BeforeAll
  static void init() {
    dynamoDbChannels = new DynamoDbChannels(dbEnhancedClient, TEST_ENV);
  }

  @Test
  void drop_channel_should_delete_channel_all_members_and_connections() {
    String channel = "pragmasoft-test";
    String hostId = randomId();
    String hostConnection = "tg:" + hostId;
    dynamoDbChannels.hostChannel(channel, hostId, hostConnection, channel);
    for (int i = 0; i < 30; i++) {
      String memberId = randomId();
      String memberConnection = "ws:" + memberId;
      String username = "test-user-" + i;
      dynamoDbChannels.joinChannel(channel, memberId, memberConnection, username);
    }

    dynamoDbChannels.dropChannel(hostConnection);

    Assertions.assertEquals(0, itemsCount(CHANNELS_TABLE));
    Assertions.assertEquals(0, itemsCount(MEMBERS_TABLE));
    Assertions.assertEquals(0, itemsCount(CONNECTIONS_TABLE));
  }
}

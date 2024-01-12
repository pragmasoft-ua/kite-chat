/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.backend.ws.infrastructure.ddb;

import java.net.URI;
import java.util.Random;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Tag("dynamodb")
@Testcontainers
class DynamoDbBaseTest {

  protected static final String TEST_ENV = "test";
  protected static final String CHANNELS_TABLE = TEST_ENV + ".Channels";
  protected static final String MEMBERS_TABLE = TEST_ENV + ".Members";
  protected static final String CONNECTIONS_TABLE = TEST_ENV + ".Connections";

  protected static DynamoDbClient dbClient;
  protected static DynamoDbEnhancedClient dbEnhancedClient;
  private static boolean isInitialized = false;

  @Container
  protected static final GenericContainer dynamodb =
      new GenericContainer(DockerImageName.parse("amazon/dynamodb-local:latest"))
          .withExposedPorts(8000)
          .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");

  @BeforeAll
  static void initDynamoDb() {
    if (isInitialized) return;
    String endpoint = "http://" + dynamodb.getHost() + ":" + dynamodb.getFirstMappedPort();
    dbClient =
        DynamoDbClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_WEST_2)
            .build();
    dbEnhancedClient = DynamoDbEnhancedClient.builder().dynamoDbClient(dbClient).build();

    isInitialized = true;
  }

  @BeforeEach
  void createTables() {
    createChannelsTable();
    createMembersTable();
    createConnectionsTable();
  }

  @AfterEach
  void deleteTables() {
    dbClient.deleteTable(DeleteTableRequest.builder().tableName(CHANNELS_TABLE).build());
    dbClient.deleteTable(DeleteTableRequest.builder().tableName(MEMBERS_TABLE).build());
    dbClient.deleteTable(DeleteTableRequest.builder().tableName(CONNECTIONS_TABLE).build());
  }

  public String randomId() {
    int leftLimit = 97; // letter 'a'
    int rightLimit = 122; // letter 'z'
    int targetStringLength = 10;
    Random random = new Random();
    StringBuilder buffer = new StringBuilder(targetStringLength);
    for (int i = 0; i < targetStringLength; i++) {
      int randomLimitedInt = leftLimit + (int) (random.nextFloat() * (rightLimit - leftLimit + 1));
      buffer.append((char) randomLimitedInt);
    }
    return buffer.toString();
  }

  public Integer itemsCount(String tableName) {
    return dbClient.scan(ScanRequest.builder().tableName(tableName).build()).count();
  }

  private void createChannelsTable() {
    dbClient.createTable(
        CreateTableRequest.builder()
            .tableName(CHANNELS_TABLE)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("name")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("name").keyType(KeyType.HASH).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }

  private void createMembersTable() {
    dbClient.createTable(
        CreateTableRequest.builder()
            .tableName(MEMBERS_TABLE)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("channelName")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("id")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder()
                    .attributeName("channelName")
                    .keyType(KeyType.HASH)
                    .build(),
                KeySchemaElement.builder().attributeName("id").keyType(KeyType.RANGE).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }

  private void createConnectionsTable() {
    dbClient.createTable(
        CreateTableRequest.builder()
            .tableName(CONNECTIONS_TABLE)
            .attributeDefinitions(
                AttributeDefinition.builder()
                    .attributeName("connector")
                    .attributeType(ScalarAttributeType.S)
                    .build(),
                AttributeDefinition.builder()
                    .attributeName("rawId")
                    .attributeType(ScalarAttributeType.S)
                    .build())
            .keySchema(
                KeySchemaElement.builder().attributeName("connector").keyType(KeyType.HASH).build(),
                KeySchemaElement.builder().attributeName("rawId").keyType(KeyType.RANGE).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }
}

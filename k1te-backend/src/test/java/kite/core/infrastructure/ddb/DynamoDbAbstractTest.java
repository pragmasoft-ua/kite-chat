/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.enhanced.dynamodb.internal.client.DefaultDynamoDbEnhancedAsyncClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbAsyncClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@DynamoDbLocalTest
abstract class DynamoDbAbstractTest {

  protected static DefaultDynamoDbEnhancedAsyncClient enhancedAsyncClient;

  @Container
  protected static final GenericContainer<?> dynamodb =
      new GenericContainer<>(DockerImageName.parse("amazon/dynamodb-local:latest"))
          .withExposedPorts(8000)
          .withCommand("-jar DynamoDBLocal.jar -inMemory -sharedDb");

  @BeforeAll
  static void initClient() {
    String endpoint = "http://" + dynamodb.getHost() + ":" + dynamodb.getFirstMappedPort();
    var dbClient =
        DynamoDbAsyncClient.builder()
            .endpointOverride(URI.create(endpoint))
            .region(Region.US_WEST_2)
            .build();
    enhancedAsyncClient =
        (DefaultDynamoDbEnhancedAsyncClient)
            DynamoDbEnhancedAsyncClient.builder().dynamoDbClient(dbClient).build();
  }

  public static Integer itemsCount(String tableName) {
    return enhancedAsyncClient
        .dynamoDbAsyncClient()
        .scan(ScanRequest.builder().tableName(tableName).build())
        .join()
        .count();
  }

  public static void deleteTable(String tableName) {
    enhancedAsyncClient
        .dynamoDbAsyncClient()
        .deleteTable(DeleteTableRequest.builder().tableName(tableName).build())
        .join();
  }

  public static void createTable(Table table) {
    enhancedAsyncClient.dynamoDbAsyncClient().createTable(table.create()).join();
  }

  @Deprecated
  // just for example
  public static void createMembers() {
    createTable(
        table("members", "channelName", gsi("byChannel", ProjectionType.KEYS_ONLY, "channel")));
  }

  static AttributeDefinition stringAttribute(String name) {
    return AttributeDefinition.builder()
        .attributeName(name)
        .attributeType(ScalarAttributeType.S)
        .build();
  }

  static KeySchemaElement hashKey(String name) {
    return KeySchemaElement.builder().attributeName(name).keyType(KeyType.HASH).build();
  }

  static KeySchemaElement rangeKey(String name) {
    return KeySchemaElement.builder().attributeName(name).keyType(KeyType.RANGE).build();
  }

  static Table table(String name, String hashKey, String rangeKey, Index... gsi) {
    return new Table(name, hashKey, rangeKey, gsi);
  }

  static Table table(String name, String hashKey, Index... gsi) {
    return new Table(name, hashKey, null, gsi);
  }

  static Index gsi(String name, ProjectionType projection, String hashKey, String rangeKey) {
    return new Index(name, projection, hashKey, rangeKey);
  }

  static Index gsi(String name, ProjectionType projection, String hashKey) {
    return new Index(name, projection, hashKey, null);
  }

  public record Table(String name, String hash, String range, Index... gsi) {

    public CreateTableRequest create() {

      var hashAttribute = stringAttribute(hash);
      var hashKey = hashKey(hash);

      var attributes =
          (null != range) ? List.of(hashAttribute, stringAttribute(range)) : List.of(hashAttribute);

      var keys = (null != range) ? List.of(hashKey, rangeKey(range)) : List.of(hashKey);

      return CreateTableRequest.builder()
          .tableName(name)
          .attributeDefinitions(attributes)
          .keySchema(keys)
          .billingMode(BillingMode.PAY_PER_REQUEST)
          .globalSecondaryIndexes(
              Arrays.stream(gsi).map(Index::create).toArray(GlobalSecondaryIndex[]::new))
          .build();
    }
  }

  public record Index(String name, ProjectionType projection, String hash, String range) {

    public GlobalSecondaryIndex create() {

      var hashKey = hashKey(hash);
      var keys = (null != range) ? List.of(hashKey, rangeKey(range)) : List.of(hashKey);

      return GlobalSecondaryIndex.builder()
          .indexName(name)
          .keySchema(keys)
          .projection(Projection.builder().projectionType(projection).build())
          .build();
    }
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023-2024 */
package kite.core.infrastructure.ddb;

import kite.core.domain.Connection;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbIgnore;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

// @DynamoDbBean
class DynamoDbConnection implements Keyed {

  private String name;

  DynamoDbConnection() {
    super();
  }

  DynamoDbConnection(Connection connection) {
    super();
  }

  @DynamoDbPartitionKey
  String getName() {
    return name;
  }

  void setName(String name) {
    this.name = name;
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

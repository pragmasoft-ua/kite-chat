/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import kite.core.domain.Connection;
import kite.core.domain.Connections;
import kite.core.domain.Member;
import kite.core.domain.Member.Id;
import kite.core.domain.Members;
import kite.core.domain.Route;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedAsyncClient;

class DynamoDbMembers implements Members, Connections {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbMembers.class);

  private final DynamoDbEnhancedAsyncClient enhancedDynamo;
  private final DynamoDbAsyncTable<DynamoDbMember> membersTable;
  private final DynamoDbAsyncTable<DynamoDbConnection> connectionsTable;

  public DynamoDbMembers(
      DynamoDbEnhancedAsyncClient enhancedDynamo,
      DynamoDbAsyncTable<DynamoDbMember> membersTable,
      DynamoDbAsyncTable<DynamoDbConnection> connectionsTable) {
    this.enhancedDynamo = enhancedDynamo;
    this.membersTable = membersTable;
    this.connectionsTable = connectionsTable;
  }

  @Override
  public Connection get(Route route) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'get'");
  }

  void put(Connection c) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'connect'");
  }

  void delete(Connection c) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'disconnect'");
  }

  @Override
  public Member get(Id memberId) {
    // TODO Auto-generated method stub
    throw new UnsupportedOperationException("Unimplemented method 'get'");
  }
}

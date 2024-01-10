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
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

class DynamoDbMembers implements Members, Connections {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbMembers.class);

  private final DynamoDbEnhancedClient enhancedDynamo;
  private final DynamoDbTable<DynamoDbMember> membersTable;
  private final DynamoDbTable<DynamoDbConnection> connectionsTable;

  public DynamoDbMembers(
      DynamoDbEnhancedClient enhancedDynamo,
      DynamoDbTable<DynamoDbMember> membersTable,
      DynamoDbTable<DynamoDbConnection> connectionsTable) {
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

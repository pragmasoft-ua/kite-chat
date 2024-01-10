/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@ExtendWith(MockitoExtension.class)
class DynamoDbCachedTableTest {

  @Mock DynamoDbTable<KeyedEntity> table;

  @Test
  @DisplayName("Returns same item from the cache")
  void returnsCachedItem() {
    KeyedEntity item = new KeyedEntity();
    item.setName("item");
    Key key = item.key();
    when(table.getItem(key)).thenReturn(item);
    var cached = new DynamoDbCachedTable<>(table);
    var first = cached.getItem(key);
    var second = cached.getItem(key);
    assertSame(first, second);
  }

  @Test
  @DisplayName("Returns null from pendingUpdates() if no updates")
  void returnsNullIfNoUpdates() {
    var cached = new DynamoDbCachedTable<>(table);
    var batch = cached.pendingUpdates();
    assertNull(batch);
  }

  @Test
  @DisplayName("Two updates of the same item results in the single write")
  void twoUpdatesCausesSingleWrite() {
    var cached = new DynamoDbCachedTable<>(table);
    when(table.tableSchema()).thenReturn(TableSchema.fromClass(KeyedEntity.class));
    KeyedEntity item = new KeyedEntity();
    item.setName("item");
    item.setValue("value1");
    cached.putItem(item);
    item.setValue("value2");
    cached.putItem(item);
    assertEquals(1, cached.pendingUpdates().writeRequests().size());
  }
}

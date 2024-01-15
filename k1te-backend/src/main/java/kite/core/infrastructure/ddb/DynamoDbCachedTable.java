/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package kite.core.infrastructure.ddb;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import ua.com.pragmasoft.k1te.backend.shared.KiteException;
import ua.com.pragmasoft.k1te.backend.shared.NotFoundException;

final class DynamoDbCachedTable<I extends Keyed> {

  private static final Logger log = LoggerFactory.getLogger(DynamoDbCachedTable.class);
  private static final int DEFAULT_CAPACITY = 4;
  private final Map<Key, I> itemsCache;
  private final Set<Key> dirtyItems;
  private final Set<Key> deletedItems;
  private final DynamoDbAsyncTable<I> table;
  private final Class<I> reifiedItemClass;

  /**
   * @param table
   */
  @SuppressWarnings("unchecked")
  DynamoDbCachedTable(DynamoDbAsyncTable<I> table, I... items) {
    // https://maciejwalkowiak.com/blog/java-reified-generics/
    this.reifiedItemClass = (Class<I>) items.getClass().componentType();
    this.table = table;
    this.itemsCache = new HashMap<>(DEFAULT_CAPACITY);
    this.dirtyItems = new HashSet<>(DEFAULT_CAPACITY);
    this.deletedItems = new HashSet<>(DEFAULT_CAPACITY);
  }

  public synchronized I getItem(Key k) {
    if (this.deletedItems.contains(k)) throw new NotFoundException(k.toString());
    return this.itemsCache.computeIfAbsent(k, this::readThrough);
  }

  public synchronized void putItem(I i) {
    Key k = i.key();
    this.itemsCache.put(k, i);
    this.dirtyItems.add(k);
    this.deletedItems.remove(k);
  }

  public synchronized I deleteItem(Key k) {
    this.deletedItems.add(k);
    this.dirtyItems.remove(k);
    return this.itemsCache.remove(k);
  }

  public synchronized I deleteItem(I i) {
    return this.deleteItem(i.key());
  }

  public synchronized WriteBatch pendingUpdates() {
    if (this.dirtyItems.isEmpty() && this.deletedItems.isEmpty()) return null;
    WriteBatch.Builder<I> batchBuilder =
        WriteBatch.builder(this.reifiedItemClass).mappedTableResource(this.table);
    for (var k : this.dirtyItems) {
      batchBuilder.addPutItem(this.itemsCache.get(k));
    }
    for (var k : this.deletedItems) {
      batchBuilder.addDeleteItem(k);
    }
    var batch = batchBuilder.build();
    if (null != batch.writeRequests()) {
      log.debug(
          "pending {} {} writes",
          batch.writeRequests().size(),
          this.reifiedItemClass.getSimpleName());
      this.clear();
    }
    return batch;
  }

  public synchronized void clear() {
    this.itemsCache.clear();
    this.dirtyItems.clear();
    this.deletedItems.clear();
  }

  private I readThrough(Key k) {
    try {
      I result = this.table.getItem(k).join();
      if (null == result) {
        throw new NotFoundException("Not found " + this.reifiedItemClass.getSimpleName());
      }
      return result;
    } catch (DynamoDbException e) {
      throw new KiteException(e.getMessage(), e);
    }
  }
}

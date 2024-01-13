/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2024 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbAsyncTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

public class WriteBatchDivider {
  private final Integer batchSizeLimit;

  private Integer requestCount;
  private final List<WriteBatch> stash;
  private final List<BatchWriteItemEnhancedRequest> requests;

  public WriteBatchDivider(int batchSizeLimit) {
    this.requestCount = 0;
    this.requests = new ArrayList<>();
    this.stash = new ArrayList<>();
    this.batchSizeLimit = batchSizeLimit;
  }

  public WriteBatchDivider() {
    this(25);
  }

  @SuppressWarnings("unchecked")
  public <T> WriteBatchDivider deleteDivide(
      List<Key> keys, DynamoDbAsyncTable<T> mappedTable, T... items) {
    Class<T> type = (Class<T>) items.getClass().componentType();
    int tookItems;
    for (int i = 0; i < keys.size(); i += tookItems) {
      int end = Math.min(i + batchSizeLimit - requestCount, keys.size());
      List<Key> sublist = new ArrayList<>(keys.subList(i, end));
      WriteBatch.Builder<T> builder = WriteBatch.builder(type).mappedTableResource(mappedTable);
      sublist.forEach(builder::addDeleteItem);
      tookItems = sublist.size();
      if (tookItems + requestCount == batchSizeLimit) {
        this.stash.add(builder.build());
        this.flush();
      } else {
        this.stash.add(builder.build());
        this.requestCount += tookItems;
      }
    }
    return this;
  }

  public List<BatchWriteItemEnhancedRequest> requests() {
    if (!stash.isEmpty()) {
      this.flush();
    }
    return this.requests;
  }

  private void flush() {
    BatchWriteItemEnhancedRequest request =
        BatchWriteItemEnhancedRequest.builder().writeBatches(this.stash).build();
    this.requests.add(request);
    this.stash.clear();
    this.requestCount = 0;
  }
}

/* LGPL 3.0 ©️ Dmytro Zemnytskyi, pragmasoft@gmail.com, 2023 */
package ua.com.pragmasoft.k1te.backend.router.infrastructure;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class DynamoDBConnection {

  private String connector;
  private String rawId;
  private String channelName;
  private String memberId;

  public DynamoDBConnection(String connector, String rawId, String channelName, String memberId) {
    this.connector = connector;
    this.rawId = rawId;
    this.channelName = channelName;
    this.memberId = memberId;
  }

  public DynamoDBConnection() {}

  @DynamoDbPartitionKey
  public String getConnector() {
    return connector;
  }

  public void setConnector(String connector) {
    this.connector = connector;
  }

  @DynamoDbSortKey
  public String getRawId() {
    return rawId;
  }

  public void setRawId(String rawId) {
    this.rawId = rawId;
  }

  public String getChannelName() {
    return channelName;
  }

  public void setChannelName(String channelName) {
    this.channelName = channelName;
  }

  public String getMemberId() {
    return memberId;
  }

  public void setMemberId(String memberId) {
    this.memberId = memberId;
  }
}

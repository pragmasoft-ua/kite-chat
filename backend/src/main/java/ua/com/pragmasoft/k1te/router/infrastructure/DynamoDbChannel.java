package ua.com.pragmasoft.k1te.router.infrastructure;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
public class DynamoDbChannel {

  public static final Duration DEFAULT_TIMEOUT = Duration.ofDays(92);

  private String name;
  private String host;
  private long ttl;

  public DynamoDbChannel() {
    super();
  }

  DynamoDbChannel(String name, String host, long ttl) {
    super();
    this.name = name;
    this.host = host;
    this.ttl = ttl;
  }

  public DynamoDbChannel(String name, String host) {
    this(name, host, Instant.now().plus(DEFAULT_TIMEOUT).getEpochSecond());
  }

  @DynamoDbPartitionKey
  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getHost() {
    return host;
  }

  /**
   * @param host the operator to set
   */
  public void setHost(String host) {
    this.host = host;
  }

  public long getTtl() {
    return ttl;
  }

  public void setTtl(long ttl) {
    this.ttl = ttl;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, host, ttl);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (!(obj instanceof DynamoDbChannel))
      return false;
    DynamoDbChannel other = (DynamoDbChannel) obj;
    return Objects.equals(name, other.name) && Objects.equals(host, other.host)
        && ttl == other.ttl;
  }

  /*
   * (non-Javadoc)
   * 
   * @see java.lang.Object#toString()
   */
  @Override
  public String toString() {
    return "DynamoDbChannel [name=" + name + ", host=" + host + ", ttl=" + ttl + "]";
  }

}

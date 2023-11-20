package ua.com.pragmasoft.k1te.backend.shared;

public class TooLargeException extends ValidationException {
  private final Long maxSize;
  private final Long actualSize;

  /**
   * Pass size in KB
   * */
  public TooLargeException(Long maxSize, Long actualSize) {
    super("The provided content is too large. Allowed max size: %dKB but actual size: %dKB".formatted(maxSize,actualSize));
    this.maxSize = maxSize;
    this.actualSize = actualSize;
  }

  public Long getMaxSize() {
    return maxSize;
  }

  public Long getActualSize() {
    return actualSize;
  }
}

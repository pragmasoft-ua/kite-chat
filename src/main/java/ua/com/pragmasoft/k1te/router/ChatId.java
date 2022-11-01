package ua.com.pragmasoft.k1te.router;

import java.util.regex.Pattern;

public record ChatId(String raw) {
  static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,32}");

  @SuppressWarnings("java:S6207") // for some reason sonar complains about duplicate constructor
  public ChatId(String raw) {
    if (!PATTERN.matcher(raw).matches()) {
      throw new IllegalArgumentException(
          "%s is invalid chat id. It must start with a letter, contain letter, digit, '_' '-' and be from 8 to 32 characters long"
              .formatted(raw));
    }
    this.raw = raw;
  }

}

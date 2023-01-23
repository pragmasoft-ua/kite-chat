package ua.com.pragmasoft.k1te.router.domain;

import java.util.Objects;
import java.util.regex.Pattern;
import ua.com.pragmasoft.k1te.shared.ValidationException;

public abstract class ChannelName {

  private static final Pattern PATTERN = Pattern.compile("[A-Za-z0-9_-]{8,32}");

  private ChannelName() {
  }

  public static String validate(String raw, String name) {
    Objects.requireNonNull(raw, name);
    if (!PATTERN.matcher(raw).matches()) {
      throw new ValidationException(
          "Invalid %s. Must start with a letter, contain letters, digits, underscore '_' dash '-' and be from 8 to 32 characters long"
              .formatted(name));

    }
    return raw;
  }

  public static String validate(String raw) {
    return validate(raw, "channel name");
  }
}

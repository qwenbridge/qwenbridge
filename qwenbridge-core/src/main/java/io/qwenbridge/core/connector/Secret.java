package io.qwenbridge.core.connector;

import java.util.Arrays;

/**
 * A resolved secret (QB-156). Its {@code toString} never shows the value, so it cannot leak into
 * logs, error messages or serialized state by accident; the value is read with {@link #reveal()}.
 */
public final class Secret {
  private final char[] value;

  public Secret(char[] value) {
    if (value == null || value.length == 0) {
      throw new IllegalArgumentException("a secret must not be empty");
    }
    this.value = value.clone();
  }

  public char[] reveal() {
    return value.clone();
  }

  public String revealString() {
    return new String(value);
  }

  @Override
  public String toString() {
    return "Secret[****]";
  }

  /** Overwrites the held value. */
  public void clear() {
    Arrays.fill(value, '\0');
  }
}

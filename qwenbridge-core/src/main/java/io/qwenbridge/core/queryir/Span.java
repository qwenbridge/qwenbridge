package io.qwenbridge.core.queryir;

import java.util.Objects;

/**
 * Evidence location in {@link QueryIR#originalText()} (QB-029, QB-038). Offsets are UTF-16 indices;
 * {@code text} must equal {@code originalText.substring(start, end)}. The validator enforces this
 * for every span, so a derived value can always be traced to what the user actually wrote.
 */
public record Span(int start, int end, String text) {

  public Span {
    Objects.requireNonNull(text, "text must not be null");
    if (start < 0 || end <= start || end - start != text.length()) {
      throw new IllegalArgumentException("Inconsistent span offsets");
    }
  }

  public static Span of(String original, int start, int end) {
    return new Span(start, end, original.substring(start, end));
  }

  public boolean matches(String original) {
    return end <= original.length() && original.substring(start, end).equals(text);
  }
}

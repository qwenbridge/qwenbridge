package io.qwenbridge.core.text;

import java.lang.Character.UnicodeScript;
import java.util.Objects;

/**
 * A contiguous span of text written in one script (QB-013). Offsets are UTF-16 indices into the
 * original text: {@code text.substring(start, end)} returns the segment. Neutral characters
 * (spaces, digits, punctuation, emoji, joiners and combining marks) belong to the surrounding
 * segment, so segments are contiguous and together cover the whole text in order.
 *
 * @param script the segment's script, {@code COMMON} for text without any letters
 */
public record ScriptSegment(int start, int end, UnicodeScript script, String text) {

  public ScriptSegment {
    Objects.requireNonNull(script, "script must not be null");
    Objects.requireNonNull(text, "text must not be null");
    if (start < 0 || end < start || end - start != text.length()) {
      throw new IllegalArgumentException("Inconsistent segment offsets");
    }
  }
}

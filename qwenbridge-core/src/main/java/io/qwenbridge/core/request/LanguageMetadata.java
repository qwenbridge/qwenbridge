package io.qwenbridge.core.request;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;

/**
 * Typed language metadata for a request (QB-003). Keeps four different things apart that the legacy
 * pipeline conflated into one string:
 *
 * <ul>
 *   <li>{@code detected} — what a detector inferred from the text ({@code und} when evidence is
 *       insufficient; never invented);
 *   <li>{@code detection} — how much the detector's answer can be trusted;
 *   <li>{@code preferences} — what the client declared or requested;
 *   <li>{@code direction} — the text's writing direction.
 * </ul>
 */
public record LanguageMetadata(
    LanguageTag detected,
    Confidence detection,
    LanguagePreferences preferences,
    TextDirection direction) {

  public LanguageMetadata {
    Objects.requireNonNull(detected, "detected must not be null");
    Objects.requireNonNull(detection, "detection must not be null");
    Objects.requireNonNull(preferences, "preferences must not be null");
    Objects.requireNonNull(direction, "direction must not be null");
  }

  /** Metadata before detection has run: undetermined language with unknown confidence. */
  public static LanguageMetadata undetected(LanguagePreferences preferences, String text) {
    return new LanguageMetadata(
        LanguageTag.UNDETERMINED, Confidence.unknown(), preferences, TextDirection.of(text));
  }
}

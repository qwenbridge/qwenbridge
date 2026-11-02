package io.qwenbridge.language;

import io.qwenbridge.core.language.LanguageDetection;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.core.request.TextDirection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Language analysis of one text (QB-008, QB-013). Keeps detection, client declarations and
 * mixed-language structure separate instead of collapsing them into a single string.
 *
 * @param detection overall detection; {@code und} when the text mixes languages or evidence is
 *     insufficient
 * @param declared language the client declared, never treated as detected
 * @param primaryLanguage legacy single-language code: the text's language, or for mixed text the
 *     language with the most letters; {@code unknown} when nothing was determined
 * @param mixed whether segments in different languages were detected
 * @param segments per-script segments with their own detection, in text order
 * @param direction dominant writing direction
 * @param configuredLanguage whether {@code primaryLanguage} is one of the configured languages
 */
public record LanguageAnalysis(
    LanguageDetection detection,
    Optional<LanguageTag> declared,
    String primaryLanguage,
    boolean mixed,
    List<SegmentLanguage> segments,
    TextDirection direction,
    boolean configuredLanguage) {

  public static final String UNKNOWN = "unknown";

  public LanguageAnalysis {
    Objects.requireNonNull(detection, "detection must not be null");
    declared = declared == null ? Optional.empty() : declared;
    Objects.requireNonNull(primaryLanguage, "primaryLanguage must not be null");
    segments = segments == null ? List.of() : List.copyOf(segments);
    Objects.requireNonNull(direction, "direction must not be null");
  }

  /** A script segment and its own language detection. Offsets are UTF-16 indices. */
  public record SegmentLanguage(
      int start, int end, String script, int letters, LanguageDetection detection) {
    public SegmentLanguage {
      Objects.requireNonNull(script, "script must not be null");
      Objects.requireNonNull(detection, "detection must not be null");
    }
  }

  public boolean primaryDetermined() {
    return !UNKNOWN.equals(primaryLanguage);
  }

  /**
   * Whether text must be translated before it can be treated as {@code target}: mixed text always
   * does; otherwise only text whose language was determined to be something else. Undetermined text
   * is not translated on a guess.
   */
  public boolean requiresTranslationTo(String target) {
    return mixed || (primaryDetermined() && !primaryLanguage.equals(target));
  }

  /** Language used to choose language-specific normalization: detected first, then declared. */
  public Optional<LanguageTag> normalizationLanguage() {
    if (primaryDetermined()) {
      return LanguageTag.parse(primaryLanguage);
    }
    return declared;
  }
}

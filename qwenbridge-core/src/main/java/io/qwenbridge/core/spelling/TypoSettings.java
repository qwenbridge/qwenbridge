package io.qwenbridge.core.spelling;

import java.util.List;
import java.util.Objects;

/**
 * Effective typo settings for one application or tenant (QB-291). Languages are raw configured
 * identifiers; they are validated against BCP 47 and the installed providers by {@link
 * TypoCorrector#languages}. Nothing is enabled by default: no languages means no correction.
 *
 * @param safeThreshold minimum confidence for {@link CorrectionMode#SAFE_AUTO_CORRECT}
 * @param aggressiveThreshold minimum confidence for {@link CorrectionMode#AGGRESSIVE}
 */
public record TypoSettings(
    boolean enabled,
    List<String> languages,
    CorrectionMode mode,
    double safeThreshold,
    double aggressiveThreshold) {

  public static final double DEFAULT_SAFE_THRESHOLD = 0.75;
  public static final double DEFAULT_AGGRESSIVE_THRESHOLD = 0.5;

  public TypoSettings {
    languages = languages == null ? List.of() : List.copyOf(languages);
    Objects.requireNonNull(mode, "mode must not be null");
    if (safeThreshold <= 0
        || safeThreshold > 1
        || aggressiveThreshold <= 0
        || aggressiveThreshold > 1)
      throw new IllegalArgumentException("thresholds must be in (0, 1]");
    if (aggressiveThreshold > safeThreshold)
      throw new IllegalArgumentException("the aggressive threshold cannot exceed the safe one");
  }

  /** The default: disabled, no languages. */
  public static TypoSettings disabled() {
    return new TypoSettings(
        false,
        List.of(),
        CorrectionMode.DISABLED,
        DEFAULT_SAFE_THRESHOLD,
        DEFAULT_AGGRESSIVE_THRESHOLD);
  }

  public static TypoSettings of(boolean enabled, List<String> languages, CorrectionMode mode) {
    return new TypoSettings(
        enabled, languages, mode, DEFAULT_SAFE_THRESHOLD, DEFAULT_AGGRESSIVE_THRESHOLD);
  }

  /** The mode in effect: {@link CorrectionMode#DISABLED} unless enabled with languages. */
  public CorrectionMode effectiveMode() {
    return enabled && !languages.isEmpty() ? mode : CorrectionMode.DISABLED;
  }
}

package io.qwenbridge.core.expansion;

import io.qwenbridge.core.request.LanguageTag;
import java.util.Objects;
import java.util.Optional;

/**
 * One configured target-language identifier and how it was resolved (QB-286). The original value is
 * kept for diagnostics next to its normalized BCP 47 form.
 *
 * @param configured the value exactly as the application owner configured it
 * @param language the normalized language, when the identifier is usable or a duplicate
 * @param note a short explanation (normalization, alias, duplicate or rejection reason)
 */
public record TargetLanguage(
    String configured, Optional<LanguageTag> language, Resolution resolution, String note) {

  public enum Resolution {
    /** Already a canonical BCP 47 tag. */
    VALID,
    /** Accepted after case normalization ({@code EN} to {@code en}, {@code zh-hans} to zh-Hans). */
    NORMALIZED,
    /** Mapped by an explicitly configured compatibility alias (for example {@code SE} to sv). */
    ALIAS,
    /** The same language as an earlier entry; it gets no second result. */
    DUPLICATE,
    /** Both a language code and a region code (for example {@code NO}); not guessed. */
    AMBIGUOUS_REGION_CODE,
    /** Not a usable target ({@code und}, {@code mul}, {@code zxx}, {@code mis}). */
    NOT_A_TARGET,
    /** Not a valid BCP 47 language tag, or a region code that is not a language. */
    INVALID
  }

  public TargetLanguage {
    Objects.requireNonNull(configured, "configured must not be null");
    language = language == null ? Optional.empty() : language;
    Objects.requireNonNull(resolution, "resolution must not be null");
    Objects.requireNonNull(note, "note must not be null");
    if (usable(resolution) && language.isEmpty())
      throw new IllegalArgumentException("a usable target needs a language");
  }

  /** Whether this entry is a target that gets its own expansion result. */
  public boolean usable() {
    return usable(resolution);
  }

  /** Whether this entry gets an entry in the per-language results (every non-duplicate does). */
  public boolean reported() {
    return resolution != Resolution.DUPLICATE;
  }

  private static boolean usable(Resolution r) {
    return r == Resolution.VALID || r == Resolution.NORMALIZED || r == Resolution.ALIAS;
  }
}

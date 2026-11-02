package io.qwenbridge.core.spelling;

import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The outcome of typo processing for one query (QB-291 C, H). The original query is always kept;
 * {@code corrected} equals {@code normalized} unless a correction was applied.
 *
 * @param activeLanguages the configured, supported languages that were considered
 * @param protectedTokens how many tokens were protected from correction
 */
public record CorrectionResult(
    Status status,
    CorrectionMode mode,
    String original,
    String normalized,
    String corrected,
    List<LanguageSupport> languages,
    Optional<LanguageTag> detectedLanguage,
    List<String> activeLanguages,
    List<TokenCorrection> corrections,
    int protectedTokens,
    List<String> explanations) {

  public enum Status {
    /** Typo correction is not enabled (the default). */
    DISABLED,
    /** Enabled, but no language is configured: nothing is assumed. */
    UNCONFIGURED,
    /** Languages are configured, but no installed provider supports any of them. */
    NO_PROVIDER,
    /** The query is reliably in a language that is not enabled. */
    LANGUAGE_NOT_ENABLED,
    /** Processed; nothing to correct. */
    NO_CHANGES,
    /** Candidates found; none applied (policy or confidence). */
    SUGGESTED,
    /** At least one correction applied. */
    CORRECTED
  }

  /** A suggested replacement and its ranking score (0..1). */
  public record Suggestion(String form, String language, double score) {}

  /**
   * One misspelled token.
   *
   * @param start UTF-16 offset in {@code normalized}
   * @param replacement the best candidate (as it would be written)
   * @param confidence 0..1 confidence in the best candidate
   * @param applied whether the policy applied it
   * @param reason why it was or was not applied
   */
  public record TokenCorrection(
      String token,
      int start,
      int end,
      String language,
      List<Suggestion> candidates,
      String replacement,
      double confidence,
      boolean applied,
      String reason) {
    public TokenCorrection {
      candidates = List.copyOf(candidates);
    }
  }

  public CorrectionResult {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(original, "original must not be null");
    Objects.requireNonNull(normalized, "normalized must not be null");
    Objects.requireNonNull(corrected, "corrected must not be null");
    languages = List.copyOf(languages);
    detectedLanguage = detectedLanguage == null ? Optional.empty() : detectedLanguage;
    activeLanguages = List.copyOf(activeLanguages);
    corrections = List.copyOf(corrections);
    explanations = List.copyOf(explanations);
  }

  public boolean changed() {
    return !corrected.equals(normalized);
  }
}

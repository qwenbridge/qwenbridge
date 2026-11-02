package io.qwenbridge.core.queryir;

import io.qwenbridge.core.request.LanguageTag;
import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Typo processing applied to the query (QueryIR 1.1, QB-291 H). The query's {@code originalText} is
 * unchanged; this records the normalized text, the corrected text and every correction with its
 * confidence and whether it was applied. Constraints are always grounded in the original text.
 *
 * @param status the engine's status, e.g. {@code CORRECTED}, {@code SUGGESTED}, {@code DISABLED}
 * @param mode the correction policy in effect
 */
public record TextCorrection(
    String status,
    String mode,
    String normalizedText,
    String correctedText,
    List<String> configuredLanguages,
    List<String> activeLanguages,
    Optional<LanguageTag> detectedLanguage,
    List<Correction> corrections) {

  /** One token's correction or suggestion; offsets are into {@code normalizedText}. */
  public record Correction(
      String token,
      int start,
      int end,
      String language,
      String replacement,
      BigDecimal confidence,
      boolean applied,
      String reason) {
    public Correction {
      Objects.requireNonNull(token, "token must not be null");
      Objects.requireNonNull(language, "language must not be null");
      Objects.requireNonNull(replacement, "replacement must not be null");
      Objects.requireNonNull(confidence, "confidence must not be null");
      Objects.requireNonNull(reason, "reason must not be null");
      if (start < 0 || end <= start) throw new IllegalArgumentException("invalid correction span");
      if (confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0)
        throw new IllegalArgumentException("confidence must be within 0..1");
    }
  }

  public TextCorrection {
    Objects.requireNonNull(status, "status must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    Objects.requireNonNull(normalizedText, "normalizedText must not be null");
    Objects.requireNonNull(correctedText, "correctedText must not be null");
    configuredLanguages = List.copyOf(configuredLanguages);
    activeLanguages = List.copyOf(activeLanguages);
    detectedLanguage = detectedLanguage == null ? Optional.empty() : detectedLanguage;
    corrections = List.copyOf(corrections);
    for (Correction c : corrections)
      if (c.end() > normalizedText.length())
        throw new IllegalArgumentException("correction span outside the normalized text");
  }
}

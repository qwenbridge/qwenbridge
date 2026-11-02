package io.qwenbridge.spelling;

import io.qwenbridge.core.spelling.CorrectionResult;
import io.qwenbridge.core.spelling.LanguageSupport;
import java.util.List;

/**
 * Typo processing as reported in API responses and SSE events (QB-291 I). The original query is
 * always included; {@code corrected} differs from {@code normalized} only when a correction was
 * applied.
 */
public record SpellingView(
    String status,
    String mode,
    String original,
    String normalized,
    String corrected,
    List<LanguageSupport> languages,
    String detectedLanguage,
    List<String> activeLanguages,
    List<CorrectionResult.TokenCorrection> corrections,
    int protectedTokens,
    List<String> explanations) {

  public SpellingView {
    languages = List.copyOf(languages);
    activeLanguages = List.copyOf(activeLanguages);
    corrections = List.copyOf(corrections);
    explanations = List.copyOf(explanations);
  }

  /** The view of an enabled result; null when typo correction is disabled (the default). */
  public static SpellingView of(CorrectionResult r) {
    if (!TypoService.reportable(r)) return null;
    return new SpellingView(
        r.status().name(),
        r.mode().name(),
        r.original(),
        r.normalized(),
        r.corrected(),
        r.languages(),
        r.detectedLanguage().map(t -> t.value()).orElse(null),
        r.activeLanguages(),
        r.corrections(),
        r.protectedTokens(),
        r.explanations());
  }
}

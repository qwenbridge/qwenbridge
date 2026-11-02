package io.qwenbridge.sdk.spelling;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Typo processing reported by the server (QB-291), on v1 analyze and v2 search responses and SSE
 * events. Present only when typo correction is enabled for the caller's tenant. {@code original} is
 * the query as sent; {@code corrected} differs from {@code normalized} only when a correction was
 * applied.
 *
 * @param status e.g. {@code CORRECTED}, {@code SUGGESTED}, {@code NO_CHANGES}, {@code UNCONFIGURED}
 * @param mode {@code DISABLED}, {@code SUGGEST_ONLY}, {@code SAFE_AUTO_CORRECT} or {@code
 *     AGGRESSIVE}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Spelling(
    String status,
    String mode,
    String original,
    String normalized,
    String corrected,
    List<Language> languages,
    String detectedLanguage,
    List<String> activeLanguages,
    List<Correction> corrections,
    int protectedTokens,
    List<String> explanations) {

  /**
   * A configured language: {@code SUPPORTED}, {@code PROVIDER_UNAVAILABLE} or {@code
   * INVALID_IDENTIFIER}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Language(String configured, String status, List<String> providers) {
    public Language {
      providers = providers == null ? List.of() : List.copyOf(providers);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Suggestion(String form, String language, double score) {}

  /**
   * One token: offsets into {@code normalized}; {@code applied} says whether the policy used it.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Correction(
      String token,
      int start,
      int end,
      String language,
      List<Suggestion> candidates,
      String replacement,
      double confidence,
      boolean applied,
      String reason) {
    public Correction {
      candidates = candidates == null ? List.of() : List.copyOf(candidates);
    }
  }

  public Spelling {
    languages = languages == null ? List.of() : List.copyOf(languages);
    activeLanguages = activeLanguages == null ? List.of() : List.copyOf(activeLanguages);
    corrections = corrections == null ? List.of() : List.copyOf(corrections);
    explanations = explanations == null ? List.of() : List.copyOf(explanations);
  }

  public boolean changed() {
    return corrected != null && !corrected.equals(normalized);
  }
}

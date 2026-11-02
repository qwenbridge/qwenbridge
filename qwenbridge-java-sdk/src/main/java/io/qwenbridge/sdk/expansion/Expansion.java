package io.qwenbridge.sdk.expansion;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Optional;

/**
 * Multilingual query expansion reported by the server (QB-286), on v1 analyze and v2 search
 * responses and the v2 SSE terminal event. Present only when expansion is enabled for the caller's
 * tenant. There is one target per configured, non-duplicate language, in configured order; {@code
 * text} is present only when a variant was produced.
 *
 * @param status {@code COMPLETE}, {@code PARTIAL}, {@code FAILED}, {@code NOT_CONFIGURED} or {@code
 *     CANCELLED}
 * @param policy {@code ALL_TARGETS}, {@code STRICT_ALL_TARGETS} or {@code BEST_EFFORT_ALL_TARGETS}
 * @param original the query as sent; never replaced
 * @param sourceText the text that was expanded (normalized, typo-corrected when applied)
 * @param retrievalTexts the source text and the distinct variants retrieval uses
 * @param explanations how the source language was established when detection was overruled
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Expansion(
    String status,
    String policy,
    String original,
    String originalRef,
    String sourceText,
    Source source,
    List<Configured> configured,
    List<String> requestedLanguages,
    List<String> successfulLanguages,
    List<String> unsupportedLanguages,
    List<Target> targets,
    List<String> retrievalTexts,
    List<String> explanations,
    long elapsedMillis) {

  /**
   * The source language: {@code basis} is {@code DETECTED}, {@code VOCABULARY}, {@code DECLARED},
   * {@code MIXED} or {@code UNDETERMINED}; {@code language} is absent when undetermined.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Source(
      String language,
      String basis,
      boolean mixed,
      List<String> mixedLanguages,
      String confidence,
      Double confidenceScore) {
    public Source {
      mixedLanguages = mixedLanguages == null ? List.of() : List.copyOf(mixedLanguages);
    }
  }

  /** A configured identifier and its resolution ({@code VALID}, {@code ALIAS}, ...). */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Configured(String configured, String language, String resolution, String note) {}

  /**
   * One configured target language. {@code status} is {@code SUCCESS}, {@code UNCHANGED}, {@code
   * LOW_CONFIDENCE}, {@code UNSUPPORTED_LANGUAGE}, {@code UNSUPPORTED_LANGUAGE_PAIR}, {@code
   * PROVIDER_UNAVAILABLE}, {@code SOURCE_UNDETERMINED}, {@code INVALID_LANGUAGE} or {@code FAILED}.
   */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Target(
      String requested,
      String language,
      String sourceLanguage,
      String originalRef,
      String status,
      String text,
      String provider,
      String confidence,
      Double confidenceScore,
      String reason,
      Preservation preservation,
      boolean identifiersPreserved,
      boolean retrieval,
      long elapsedMillis) {

    /** Whether a variant was produced and it kept the query's meaning. */
    public boolean succeeded() {
      return "SUCCESS".equals(status) || "UNCHANGED".equals(status);
    }
  }

  /** Meaning preservation of a translation: kinds, directions and counts only. */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Preservation(String status, List<Issue> issues, List<String> unverified) {
    public Preservation {
      issues = issues == null ? List.of() : List.copyOf(issues);
      unverified = unverified == null ? List.of() : List.copyOf(unverified);
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Issue(String kind, String direction, int count) {}

  public Expansion {
    configured = configured == null ? List.of() : List.copyOf(configured);
    requestedLanguages = requestedLanguages == null ? List.of() : List.copyOf(requestedLanguages);
    successfulLanguages =
        successfulLanguages == null ? List.of() : List.copyOf(successfulLanguages);
    unsupportedLanguages =
        unsupportedLanguages == null ? List.of() : List.copyOf(unsupportedLanguages);
    targets = targets == null ? List.of() : List.copyOf(targets);
    retrievalTexts = retrievalTexts == null ? List.of() : List.copyOf(retrievalTexts);
    explanations = explanations == null ? List.of() : List.copyOf(explanations);
  }

  /** The result for a normalized target language, such as {@code sv}. */
  public Optional<Target> target(String language) {
    return targets.stream().filter(t -> language.equals(t.language())).findFirst();
  }

  /** The produced variants that kept the query's meaning. */
  public List<Target> successful() {
    return targets.stream().filter(Target::succeeded).toList();
  }
}

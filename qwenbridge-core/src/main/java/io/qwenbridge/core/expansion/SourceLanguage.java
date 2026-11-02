package io.qwenbridge.core.expansion;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * The detected language of the query (QB-286), independent of the configured targets.
 *
 * @param language the detected language, if any
 * @param reliable whether detection was reliable; an unreliable guess is never used as a source
 * @param mixed whether the query mixes languages
 * @param mixedLanguages the languages found in a mixed query, when known
 * @param basis where the language comes from
 */
public record SourceLanguage(
    Optional<LanguageTag> language,
    boolean reliable,
    boolean mixed,
    List<LanguageTag> mixedLanguages,
    Confidence confidence,
    Basis basis) {

  public static final LanguageTag MIXED = new LanguageTag("mul");

  public enum Basis {
    /** Detected from the text. */
    DETECTED,
    /**
     * Detection was inconclusive; the language the client declared is used (documented fallback).
     */
    DECLARED,
    /**
     * Detection was statistical without a score, or failed; installed providers know at least half
     * of the query's words in this language (the vocabulary-evidence rule, as in QB-291).
     */
    VOCABULARY,
    /** The text mixes languages. */
    MIXED,
    /** No language could be established; nothing is guessed. */
    UNDETERMINED
  }

  /** Derives the basis from the other components. */
  public SourceLanguage(
      Optional<LanguageTag> language,
      boolean reliable,
      boolean mixed,
      List<LanguageTag> mixedLanguages,
      Confidence confidence) {
    this(
        language,
        reliable,
        mixed,
        mixedLanguages,
        confidence,
        mixed
            ? Basis.MIXED
            : reliable && language != null && language.isPresent()
                ? Basis.DETECTED
                : Basis.UNDETERMINED);
  }

  public SourceLanguage {
    language = language == null ? Optional.empty() : language;
    mixedLanguages = mixedLanguages == null ? List.of() : List.copyOf(mixedLanguages);
    Objects.requireNonNull(confidence, "confidence must not be null");
    Objects.requireNonNull(basis, "basis must not be null");
  }

  /** A language established by provider vocabulary evidence. */
  public static SourceLanguage vocabulary(LanguageTag language) {
    return new SourceLanguage(
        Optional.of(language), true, false, List.of(), Confidence.heuristic(), Basis.VOCABULARY);
  }

  /** A language the client declared, used only because detection was inconclusive. */
  public static SourceLanguage declared(LanguageTag language) {
    return new SourceLanguage(
        Optional.of(language), true, false, List.of(), Confidence.unknown(), Basis.DECLARED);
  }

  public static SourceLanguage undetermined() {
    return new SourceLanguage(Optional.empty(), false, false, List.of(), Confidence.unknown());
  }

  public static SourceLanguage of(LanguageTag language) {
    return new SourceLanguage(
        Optional.of(language), true, false, List.of(), Confidence.heuristic());
  }

  /** The tag sent to providers: {@code mul} when mixed, {@code und} when not reliable. */
  public LanguageTag tag() {
    if (mixed) return MIXED;
    return reliable ? language.orElse(LanguageTag.UNDETERMINED) : LanguageTag.UNDETERMINED;
  }
}

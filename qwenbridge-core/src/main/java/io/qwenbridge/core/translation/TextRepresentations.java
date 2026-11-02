package io.qwenbridge.core.translation;

import io.qwenbridge.core.confidence.Confidence;
import io.qwenbridge.core.request.LanguageTag;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The original, normalized and translated forms of a query, kept separately with how each was
 * derived (QB-017). The original is never replaced: evidence spans, audit and safety always refer
 * to it. Derived forms are inputs to matching only.
 *
 * @param original exactly what the user sent
 * @param normalized the meaning-preserving search form, when normalization ran
 * @param translated the accepted translation, when one was produced
 * @param transformations every derivation in the order it was applied
 */
public record TextRepresentations(
    String original,
    Optional<String> normalized,
    Optional<TranslatedText> translated,
    List<Transformation> transformations) {

  /** An accepted translation and what is known about it. */
  public record TranslatedText(
      String text,
      LanguageTag source,
      LanguageTag target,
      String providerId,
      Confidence confidence,
      PreservationReport.Status preservation) {

    public TranslatedText {
      Objects.requireNonNull(text, "text must not be null");
      Objects.requireNonNull(source, "source must not be null");
      Objects.requireNonNull(target, "target must not be null");
      Objects.requireNonNull(providerId, "providerId must not be null");
      Objects.requireNonNull(confidence, "confidence must not be null");
      Objects.requireNonNull(preservation, "preservation must not be null");
    }
  }

  /**
   * One derivation step.
   *
   * @param from representation it was derived from ({@code original} or {@code normalized})
   * @param to representation it produced
   * @param actor component that applied it, e.g. {@code search-normalization-v1} or a provider id
   * @param details non-sensitive metadata (steps applied, languages, preservation status)
   */
  public record Transformation(
      Kind kind, String from, String to, String actor, Map<String, String> details) {

    public enum Kind {
      NORMALIZATION,
      TRANSLATION
    }

    public Transformation {
      Objects.requireNonNull(kind, "kind must not be null");
      Objects.requireNonNull(from, "from must not be null");
      Objects.requireNonNull(to, "to must not be null");
      Objects.requireNonNull(actor, "actor must not be null");
      details = details == null ? Map.of() : Map.copyOf(details);
    }
  }

  public static final String ORIGINAL = "original";
  public static final String NORMALIZED = "normalized";
  public static final String TRANSLATED = "translated";

  public TextRepresentations {
    Objects.requireNonNull(original, "original must not be null");
    normalized = normalized == null ? Optional.empty() : normalized;
    translated = translated == null ? Optional.empty() : translated;
    transformations = transformations == null ? List.of() : List.copyOf(transformations);
  }

  public static TextRepresentations of(String original) {
    return new TextRepresentations(original, Optional.empty(), Optional.empty(), List.of());
  }

  /** Records the search-normalized form; normalization happens once, before translation. */
  public TextRepresentations withNormalized(
      String text, String normalizer, List<String> stepsApplied) {
    if (normalized.isPresent() || translated.isPresent()) {
      throw new IllegalStateException("normalization must be recorded once, before translation");
    }
    List<Transformation> steps = new ArrayList<>(transformations);
    steps.add(
        new Transformation(
            Transformation.Kind.NORMALIZATION,
            ORIGINAL,
            NORMALIZED,
            normalizer,
            Map.of("steps", String.join(",", stepsApplied))));
    return new TextRepresentations(original, Optional.of(text), translated, steps);
  }

  /** The text translation should start from: normalized when available, else the original. */
  public String translationInput() {
    return normalized.orElse(original);
  }

  /**
   * Records an outcome of the {@link TranslationStage}. Only an accepted translation becomes the
   * {@code translated} form; any other outcome leaves the representations unchanged.
   */
  public TextRepresentations withTranslation(TranslationOutcome outcome) {
    Objects.requireNonNull(outcome, "outcome must not be null");
    if (translated.isPresent()) {
      throw new IllegalStateException("a translation is already recorded");
    }
    Optional<TranslationResult.Translated> accepted = outcome.translated();
    if (accepted.isEmpty()) {
      return this;
    }
    TranslationResult.Translated t = accepted.get();
    PreservationReport.Status status =
        outcome
            .preservation()
            .map(PreservationReport::status)
            .orElse(PreservationReport.Status.PARTIALLY_VERIFIED);
    List<Transformation> steps = new ArrayList<>(transformations);
    steps.add(
        new Transformation(
            Transformation.Kind.TRANSLATION,
            normalized.isPresent() ? NORMALIZED : ORIGINAL,
            TRANSLATED,
            t.providerId(),
            Map.of(
                "source", t.source().value(),
                "target", t.target().value(),
                "preservation", status.name())));
    return new TextRepresentations(
        original,
        normalized,
        Optional.of(
            new TranslatedText(
                t.text(), t.source(), t.target(), t.providerId(), t.confidence(), status)),
        steps);
  }
}

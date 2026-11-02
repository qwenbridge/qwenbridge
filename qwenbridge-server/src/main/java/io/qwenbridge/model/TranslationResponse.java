package io.qwenbridge.model;

import io.qwenbridge.core.translation.PreservationReport;
import io.qwenbridge.core.translation.TranslationResult;
import io.qwenbridge.pipeline.result.TranslationStepResult;
import java.util.List;

/**
 * Public view of the optional translation stage (QB-015–QB-019). Issues carry kind, direction and
 * count only, never user values.
 *
 * @param status TRANSLATED, SKIPPED, UNSUPPORTED (nothing could serve the request), FAILED (an
 *     attempt failed or did not preserve meaning) or NOT_RUN (the pipeline stopped earlier)
 * @param reason the skip or failure reason, e.g. NOT_REQUESTED, NO_PROVIDER, NOT_PRESERVED
 * @param confidence the provider's typed confidence; a value only for MODEL_SCORE
 */
public record TranslationResponse(
    String status,
    String reason,
    String provider,
    String sourceLanguage,
    String targetLanguage,
    Confidence confidence,
    Preservation preservation,
    long elapsedMs) {

  public record Confidence(String kind, Double value) {}

  public record Preservation(String status, List<Issue> issues, List<String> unverified) {
    public Preservation {
      issues = issues == null ? List.of() : List.copyOf(issues);
      unverified = unverified == null ? List.of() : List.copyOf(unverified);
    }

    static Preservation from(PreservationReport report) {
      return new Preservation(
          report.status().name(),
          report.issues().stream()
              .map(i -> new Issue(i.kind().name(), i.direction().name(), i.count()))
              .toList(),
          report.unverified().stream().map(Enum::name).sorted().toList());
    }
  }

  public record Issue(String kind, String direction, int count) {}

  public static TranslationResponse notRun() {
    return new TranslationResponse("NOT_RUN", null, null, null, null, null, null, 0);
  }

  public static TranslationResponse from(TranslationStepResult step) {
    if (step == null) {
      return notRun();
    }
    String source = step.source().value();
    String target = step.target().map(Object::toString).orElse(null);
    Preservation preservation = step.outcome().preservation().map(Preservation::from).orElse(null);
    long elapsed = step.outcome().elapsed().toMillis();
    return switch (step.outcome().result()) {
      case TranslationResult.Translated t ->
          new TranslationResponse(
              "TRANSLATED",
              null,
              t.providerId(),
              t.source().value(),
              t.target().value(),
              new Confidence(
                  t.confidence().kind().name(),
                  t.confidence().value().isPresent() ? t.confidence().value().getAsDouble() : null),
              preservation,
              elapsed);
      case TranslationResult.Skipped s ->
          new TranslationResponse(
              "SKIPPED", s.reason().name(), null, source, target, null, null, elapsed);
      case TranslationResult.Failed f ->
          new TranslationResponse(
              f.failure().unsupported() ? "UNSUPPORTED" : "FAILED",
              f.failure().name(),
              f.providerId().orElse(null),
              source,
              target,
              null,
              preservation,
              elapsed);
    };
  }
}

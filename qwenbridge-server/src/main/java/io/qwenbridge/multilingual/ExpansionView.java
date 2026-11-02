package io.qwenbridge.multilingual;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.qwenbridge.core.expansion.ExpansionResult;
import io.qwenbridge.core.expansion.TargetResult;
import io.qwenbridge.core.expansion.TargetStatus;
import io.qwenbridge.core.translation.PreservationReport;
import java.util.List;

/**
 * Multilingual expansion as reported in API responses and SSE terminal events (QB-286). Present
 * only once expansion is enabled for the caller. Every configured target appears, in configured
 * order; {@code text} is present only for a produced variant. Preservation issues expose kinds,
 * directions and counts, never the user's values.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ExpansionView(
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

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Source(
      String language,
      String basis,
      boolean mixed,
      List<String> mixedLanguages,
      String confidence,
      Double confidenceScore) {
    public Source {
      mixedLanguages = List.copyOf(mixedLanguages);
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record Configured(String configured, String language, String resolution, String note) {}

  @JsonInclude(JsonInclude.Include.NON_NULL)
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
      long elapsedMillis) {}

  public record Preservation(String status, List<Issue> issues, List<String> unverified) {
    public Preservation {
      issues = List.copyOf(issues);
      unverified = List.copyOf(unverified);
    }
  }

  public record Issue(String kind, String direction, int count) {}

  public ExpansionView {
    configured = List.copyOf(configured);
    requestedLanguages = List.copyOf(requestedLanguages);
    successfulLanguages = List.copyOf(successfulLanguages);
    unsupportedLanguages = List.copyOf(unsupportedLanguages);
    targets = List.copyOf(targets);
    retrievalTexts = List.copyOf(retrievalTexts);
    explanations = List.copyOf(explanations);
  }

  /** The view of an enabled expansion; null when expansion is disabled (the default). */
  public static ExpansionView of(ExpansionResult r) {
    if (r == null || !r.reportable()) return null;
    var s = r.source();
    var source =
        new Source(
            s.tag().isUndetermined() ? null : s.tag().value(),
            s.basis().name(),
            s.mixed(),
            s.mixedLanguages().stream().map(t -> t.value()).toList(),
            s.confidence().kind().name(),
            s.confidence().value().isPresent() ? s.confidence().value().getAsDouble() : null);
    List<Target> targets = r.targets().stream().map(ExpansionView::target).toList();
    return new ExpansionView(
        r.status().name(),
        r.policy().name(),
        r.original(),
        r.originalRef(),
        r.sourceText(),
        source,
        r.configured().stream()
            .map(
                c ->
                    new Configured(
                        c.configured(),
                        c.language().map(t -> t.value()).orElse(null),
                        c.resolution().name(),
                        c.note()))
            .toList(),
        r.targets().stream().map(TargetResult::requested).toList(),
        r.targets().stream()
            .filter(t -> t.status().succeeded())
            .map(t -> t.language().map(l -> l.value()).orElse(t.requested()))
            .toList(),
        r.targets().stream()
            .filter(t -> unsupported(t.status()))
            .map(TargetResult::requested)
            .toList(),
        targets,
        r.retrievalTexts(),
        r.explanations(),
        r.elapsedMillis());
  }

  static boolean unsupported(TargetStatus s) {
    return s == TargetStatus.UNSUPPORTED_LANGUAGE
        || s == TargetStatus.UNSUPPORTED_LANGUAGE_PAIR
        || s == TargetStatus.PROVIDER_UNAVAILABLE
        || s == TargetStatus.SOURCE_UNDETERMINED
        || s == TargetStatus.INVALID_LANGUAGE;
  }

  private static Target target(TargetResult t) {
    return new Target(
        t.requested(),
        t.language().map(l -> l.value()).orElse(null),
        t.sourceLanguage().map(l -> l.value()).orElse(null),
        t.originalRef(),
        t.status().name(),
        t.text().orElse(null),
        t.provider().orElse(null),
        t.confidence().kind().name(),
        t.confidence().value().isPresent() ? t.confidence().value().getAsDouble() : null,
        t.reason().orElse(null),
        t.preservation().map(ExpansionView::preservation).orElse(null),
        t.identifiersPreserved(),
        t.retrieval(),
        t.elapsedMillis());
  }

  private static Preservation preservation(PreservationReport p) {
    return new Preservation(
        p.status().name(),
        p.issues().stream()
            .map(i -> new Issue(i.kind().name(), i.direction().name(), i.count()))
            .toList(),
        p.unverified().stream().map(Enum::name).sorted().toList());
  }
}

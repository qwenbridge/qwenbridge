package io.qwenbridge.model;

import io.qwenbridge.core.language.LanguageDetection;
import io.qwenbridge.core.request.LanguageTag;
import io.qwenbridge.language.LanguageAnalysis;
import java.util.List;

/**
 * Typed language information in v1 responses (QB-003, QB-008, QB-013). Additive companion to the
 * legacy {@code language} string; {@code und} and {@code UNKNOWN} confidence mean "not determined",
 * never a guess. {@code confidenceValue} is a model score, not a probability.
 */
public record LanguageAnalysisResponse(
    String detected,
    String confidenceKind,
    Double confidenceValue,
    String detector,
    String declared,
    String primaryLanguage,
    boolean mixed,
    String direction,
    boolean configuredLanguage,
    List<Segment> segments) {

  public LanguageAnalysisResponse {
    segments = segments == null ? List.of() : List.copyOf(segments);
  }

  public record Segment(
      int start, int end, String script, String language, String confidenceKind) {}

  public static LanguageAnalysisResponse from(LanguageAnalysis analysis) {
    if (analysis == null) {
      return null;
    }
    LanguageDetection detection = analysis.detection();
    return new LanguageAnalysisResponse(
        detection.language().value(),
        detection.confidence().kind().name(),
        detection.confidence().value().isPresent()
            ? detection.confidence().value().getAsDouble()
            : null,
        detection.detector(),
        analysis.declared().map(LanguageTag::value).orElse(null),
        analysis.primaryLanguage(),
        analysis.mixed(),
        analysis.direction().name(),
        analysis.configuredLanguage(),
        analysis.segments().stream()
            .map(
                s ->
                    new Segment(
                        s.start(),
                        s.end(),
                        s.script(),
                        s.detection().language().value(),
                        s.detection().confidence().kind().name()))
            .toList());
  }
}

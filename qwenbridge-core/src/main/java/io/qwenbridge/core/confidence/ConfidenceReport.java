package io.qwenbridge.core.confidence;

import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Per-component confidence of one understanding result and a deliberately conservative overall
 * value (QB-078). Retrieval scores are never part of it (QB-079): they measure how well a document
 * matched, not how sure the system is about the query.
 *
 * <p>Combination rule ({@value #METHOD}): any UNKNOWN component makes the overall UNKNOWN; only
 * when every component is a {@code MODEL_SCORE} is the overall a number (their minimum); when all
 * are DETERMINISTIC the overall is DETERMINISTIC; any other mix is HEURISTIC with no number. {@code
 * calibrated} is true only if every score went through a calibration step.
 */
public record ConfidenceReport(
    List<ComponentConfidence> components, Confidence overall, String method, boolean calibrated) {

  public static final String METHOD = "weakest-link-v1";

  public ConfidenceReport {
    components = List.copyOf(components);
    Objects.requireNonNull(overall, "overall must not be null");
    Objects.requireNonNull(method, "method must not be null");
    if (calibrated && overall.kind() != Confidence.Kind.MODEL_SCORE) {
      throw new IllegalArgumentException("only numeric scores can be calibrated");
    }
  }

  public static ConfidenceReport of(List<ComponentConfidence> components, boolean calibrated) {
    return new ConfidenceReport(
        components,
        combine(components),
        METHOD,
        calibrated && allScores(components) && !components.isEmpty());
  }

  static Confidence combine(List<ComponentConfidence> components) {
    if (components.isEmpty()
        || components.stream().anyMatch(c -> c.confidence().kind() == Confidence.Kind.UNKNOWN)) {
      return Confidence.unknown();
    }
    if (allScores(components)) {
      OptionalDouble min =
          components.stream().mapToDouble(c -> c.confidence().value().getAsDouble()).min();
      return Confidence.modelScore(min.getAsDouble());
    }
    if (components.stream().allMatch(c -> c.confidence().kind() == Confidence.Kind.DETERMINISTIC)) {
      return Confidence.deterministic();
    }
    return Confidence.heuristic();
  }

  private static boolean allScores(List<ComponentConfidence> components) {
    return components.stream().allMatch(c -> c.confidence().kind() == Confidence.Kind.MODEL_SCORE);
  }
}

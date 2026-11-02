package io.qwenbridge.core.confidence;

import java.util.Objects;
import java.util.OptionalDouble;

/**
 * How much a derived value can be trusted, and on what basis (QB-019, QB-068, QB-078). A number is
 * present only for {@link Kind#MODEL_SCORE}; every other kind is deliberately numberless so that no
 * component invents a probability. Even a model score is not a calibrated probability unless a
 * calibration step says so explicitly.
 */
public record Confidence(Kind kind, OptionalDouble value) {

  public enum Kind {
    /** No usable signal; nothing may be inferred. */
    UNKNOWN,
    /** An exact, deterministic rule matched (for example a parsed number). Not a probability. */
    DETERMINISTIC,
    /** A heuristic decided; no probability is implied. */
    HEURISTIC,
    /** A model score in [0,1]; not necessarily a calibrated probability. */
    MODEL_SCORE,
    /**
     * A model decided but its score is not informative (for example saturated at 1.0 regardless of
     * correctness), so no number is reported.
     */
    MODEL_DECISION
  }

  public Confidence {
    Objects.requireNonNull(kind, "kind must not be null");
    value = value == null ? OptionalDouble.empty() : value;
    if (kind == Kind.MODEL_SCORE) {
      double v = value.orElseThrow(() -> new IllegalArgumentException("MODEL_SCORE needs value"));
      if (Double.isNaN(v) || v < 0.0 || v > 1.0) {
        throw new IllegalArgumentException("MODEL_SCORE must be within [0,1]");
      }
    } else if (value.isPresent()) {
      throw new IllegalArgumentException(kind + " must not carry a numeric value");
    }
  }

  public static Confidence unknown() {
    return new Confidence(Kind.UNKNOWN, OptionalDouble.empty());
  }

  public static Confidence deterministic() {
    return new Confidence(Kind.DETERMINISTIC, OptionalDouble.empty());
  }

  public static Confidence heuristic() {
    return new Confidence(Kind.HEURISTIC, OptionalDouble.empty());
  }

  public static Confidence modelDecision() {
    return new Confidence(Kind.MODEL_DECISION, OptionalDouble.empty());
  }

  public static Confidence modelScore(double score) {
    return new Confidence(Kind.MODEL_SCORE, OptionalDouble.of(score));
  }
}

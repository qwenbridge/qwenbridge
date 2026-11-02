package io.qwenbridge.core.confidence;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Measures how well numeric scores match observed correctness (QB-083): Brier score, expected
 * calibration error (ECE) over equal-width bins, and the reliability table.
 *
 * <p>ECE is reported only when it is meaningful: at least {@code minimumObservations} labelled
 * observations. Below that the report says so and carries no ECE, rather than a number that looks
 * precise. Categorical confidences (HEURISTIC, MODEL_DECISION) have no number and cannot be
 * calibrated here.
 */
public final class Calibration {

  public static final int DEFAULT_BINS = 10;
  public static final int DEFAULT_MINIMUM = 50;

  private Calibration() {}

  /** A score in [0,1] and whether the scored answer was correct. */
  public record Observation(double score, boolean correct) {
    public Observation {
      if (!Double.isFinite(score) || score < 0 || score > 1) {
        throw new IllegalArgumentException("score must be within [0,1]");
      }
    }
  }

  public record Bin(double lower, double upper, int count, double meanScore, double accuracy) {}

  public record Report(
      int observations,
      double brierScore,
      OptionalDouble expectedCalibrationError,
      List<Bin> bins,
      boolean meaningful,
      String note) {
    public Report {
      bins = List.copyOf(bins);
      Objects.requireNonNull(expectedCalibrationError, "ece must not be null");
      Objects.requireNonNull(note, "note must not be null");
    }
  }

  public static Report report(List<Observation> observations) {
    return report(observations, DEFAULT_BINS, DEFAULT_MINIMUM);
  }

  public static Report report(List<Observation> observations, int bins, int minimumObservations) {
    if (observations.isEmpty()) {
      throw new IllegalArgumentException("no observations");
    }
    if (bins < 1) {
      throw new IllegalArgumentException("bins must be positive");
    }
    int n = observations.size();
    double brier = 0;
    for (Observation o : observations) {
      brier += Math.pow(o.score() - (o.correct() ? 1 : 0), 2);
    }
    brier /= n;
    List<Bin> table = new ArrayList<>();
    double ece = 0;
    for (int b = 0; b < bins; b++) {
      int count = 0;
      int correct = 0;
      double total = 0;
      for (Observation o : observations) {
        if (Math.min(bins - 1, (int) (o.score() * bins)) == b) {
          count++;
          total += o.score();
          if (o.correct()) {
            correct++;
          }
        }
      }
      if (count > 0) {
        double mean = total / count;
        double accuracy = (double) correct / count;
        table.add(new Bin((double) b / bins, (double) (b + 1) / bins, count, mean, accuracy));
        ece += (double) count / n * Math.abs(mean - accuracy);
      }
    }
    boolean meaningful = n >= minimumObservations;
    return new Report(
        n,
        brier,
        meaningful ? OptionalDouble.of(ece) : OptionalDouble.empty(),
        table,
        meaningful,
        meaningful
            ? "ECE over " + bins + " equal-width bins"
            : "fewer than " + minimumObservations + " observations; ECE not reported");
  }
}

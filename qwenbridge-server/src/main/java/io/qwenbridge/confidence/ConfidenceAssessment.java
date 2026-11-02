package io.qwenbridge.confidence;

import java.util.Map;

/** Model-derived confidence signals; not a calibrated probability. */
public record ConfidenceAssessment(
    double value, String method, boolean calibrated, Map<String, Double> signals) {
  public ConfidenceAssessment {
    signals = Map.copyOf(signals);
  }
}

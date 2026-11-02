package io.qwenbridge.core.confidence;

import java.util.Objects;

/**
 * One component's confidence and where it came from (QB-078).
 *
 * @param source the detector, model or rule set that produced it
 */
public record ComponentConfidence(
    ConfidenceComponent component, Confidence confidence, String source) {

  public ComponentConfidence {
    Objects.requireNonNull(component, "component must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    Objects.requireNonNull(source, "source must not be null");
  }
}

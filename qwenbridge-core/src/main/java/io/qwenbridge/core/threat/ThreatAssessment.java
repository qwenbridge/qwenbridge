package io.qwenbridge.core.threat;

import java.util.List;
import java.util.Objects;

/**
 * The result of inspecting one text (QB-064, QB-069).
 *
 * @param action the strictest action required by any signal under the policy
 * @param signals every signal, rule and model
 * @param modelStatus whether a model classifier took part
 * @param failedDetectors rule detectors that failed; any failure makes the action at least BLOCK in
 *     user input and QUARANTINE elsewhere (mandatory controls fail closed)
 */
public record ThreatAssessment(
    TrustZone zone,
    ThreatAction action,
    List<ThreatSignal> signals,
    ModelStatus modelStatus,
    List<String> failedDetectors) {

  public enum ModelStatus {
    /** No model classifier is configured; rules alone decided (the defined baseline). */
    NOT_CONFIGURED,
    /** A model is configured but failed or timed out; rules alone decided (degraded mode). */
    UNAVAILABLE,
    /** A model classified the text; it could only raise the action. */
    EVALUATED,
    /** Rules already required the strictest handling, so the model was not consulted. */
    NOT_EVALUATED
  }

  public ThreatAssessment {
    Objects.requireNonNull(zone, "zone must not be null");
    Objects.requireNonNull(action, "action must not be null");
    signals = List.copyOf(signals);
    Objects.requireNonNull(modelStatus, "modelStatus must not be null");
    failedDetectors = List.copyOf(failedDetectors);
  }

  /** Whether the model was configured but did not contribute. */
  public boolean degraded() {
    return modelStatus == ModelStatus.UNAVAILABLE;
  }
}

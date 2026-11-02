package io.qwenbridge.core.threat;

import io.qwenbridge.core.confidence.Confidence;
import java.util.Objects;

/**
 * One detected threat indication (QB-064, QB-068).
 *
 * @param detectorId detector that produced it
 * @param ruleId rule or model label inside the detector
 * @param evidence what matched, described in the detector's words; never a copy of the input text
 * @param confidence how much to trust it; rule matches are {@code HEURISTIC} or {@code
 *     DETERMINISTIC}, model findings {@code MODEL_SCORE}/{@code MODEL_DECISION}. Hand-set numbers
 *     are not confidences and are not reported.
 */
public record ThreatSignal(
    ThreatCategory category,
    ThreatSeverity severity,
    Confidence confidence,
    Source source,
    String detectorId,
    String ruleId,
    String evidence) {

  /** Whether a signal came from deterministic rules or from a model. */
  public enum Source {
    RULE,
    MODEL
  }

  public ThreatSignal {
    Objects.requireNonNull(category, "category must not be null");
    Objects.requireNonNull(severity, "severity must not be null");
    Objects.requireNonNull(confidence, "confidence must not be null");
    Objects.requireNonNull(source, "source must not be null");
    Objects.requireNonNull(detectorId, "detectorId must not be null");
    Objects.requireNonNull(ruleId, "ruleId must not be null");
    Objects.requireNonNull(evidence, "evidence must not be null");
  }
}

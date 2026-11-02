package io.qwenbridge.model;

import io.qwenbridge.core.threat.ThreatAssessment;
import java.util.List;

/**
 * Public view of the typed threat assessment (QB-064–QB-069). Evidence is the rule's own
 * description, never the user's text; confidence has a value only for real model scores.
 *
 * @param action ALLOW, REVIEW, QUARANTINE or BLOCK; a content-safety decision, never an
 *     authorization
 * @param modelStatus NOT_CONFIGURED, UNAVAILABLE (rules decided alone) or EVALUATED
 */
public record ThreatAssessmentResponse(
    String zone,
    String action,
    String modelStatus,
    boolean degraded,
    List<String> failedDetectors,
    List<Signal> signals) {

  public ThreatAssessmentResponse {
    failedDetectors = failedDetectors == null ? List.of() : List.copyOf(failedDetectors);
    signals = signals == null ? List.of() : List.copyOf(signals);
  }

  public record Signal(
      String category,
      String severity,
      String source,
      String confidence,
      String detector,
      String rule,
      String evidence) {}

  public static ThreatAssessmentResponse from(ThreatAssessment assessment) {
    if (assessment == null) {
      return null;
    }
    return new ThreatAssessmentResponse(
        assessment.zone().name(),
        assessment.action().name(),
        assessment.modelStatus().name(),
        assessment.degraded(),
        assessment.failedDetectors(),
        assessment.signals().stream()
            .map(
                s ->
                    new Signal(
                        s.category().name(),
                        s.severity().name(),
                        s.source().name(),
                        s.confidence().kind().name(),
                        s.detectorId(),
                        s.ruleId(),
                        s.evidence()))
            .toList());
  }
}

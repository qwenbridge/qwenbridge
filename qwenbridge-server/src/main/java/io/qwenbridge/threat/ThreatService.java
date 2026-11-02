package io.qwenbridge.threat;

import io.qwenbridge.threat.correlation.ThreatCorrelationService;
import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.correlation.ThreatRiskProfile;
import io.qwenbridge.threat.decision.ThreatDecisionEngine;
import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.detector.ThreatDetectorRegistry;
import io.qwenbridge.threat.explanation.ThreatExplanationBuilder;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatDecision;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.scoring.ThreatScoringService;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class ThreatService {

  private final ThreatDetectorRegistry detectorRegistry;
  private final ThreatScoringService scoringService;
  private final ThreatDecisionEngine decisionEngine;
  private final ThreatCorrelationService correlationService;
  private final ThreatExplanationBuilder explanationBuilder;

  public ThreatResult analyze(String query) {
    return toResult(analyzeDetailed(query));
  }

  public ThreatResult toResult(ThreatAnalysis analysis) {
    List<String> reasons = new ArrayList<>();

    reasons.addAll(
        analysis.findings().stream()
            .map(ThreatFinding::reason)
            .filter(reason -> reason != null && !reason.isBlank())
            .toList());

    reasons.addAll(
        analysis.riskProfile().correlations().stream()
            .map(correlation -> correlation.reason())
            .filter(reason -> reason != null && !reason.isBlank())
            .toList());

    return new ThreatResult(analysis.safe(), reasons);
  }

  public ThreatAnalysis analyzeDetailed(String query) {
    String safeQuery = query == null ? "" : query;
    List<ThreatFinding> findings = new ArrayList<>();

    for (ThreatDetector detector : detectorRegistry.detectors()) {
      List<ThreatFinding> detectorFindings;
      try {
        detectorFindings = detector.detect(safeQuery);
      } catch (RuntimeException ex) {
        // Rules are a mandatory control (QB-069): a failing detector fails closed.
        detectorFindings =
            List.of(
                new ThreatFinding(
                    detector.type(),
                    io.qwenbridge.threat.model.ThreatSeverity.CRITICAL,
                    1.0,
                    0.0,
                    detector.name(),
                    "detector failure",
                    "Threat detector failed; request blocked."));
      }

      if (detectorFindings != null && !detectorFindings.isEmpty()) {
        findings.addAll(detectorFindings);
      }
    }

    ThreatRiskProfile riskProfile = correlationService.correlate(findings);

    ThreatDecision decision = decide(scoringService.maxSeverity(findings), riskProfile.riskLevel());

    ThreatAnalysis analysis = ThreatAnalysis.from(findings, decision, riskProfile);

    return analysis.withExplanation(explanationBuilder.build(analysis));
  }

  /**
   * Correlations of several categories escalate the risk level; otherwise the strictest single
   * finding decides through the severity policy (QB-068). The hand-set rule scores are still
   * reported but no longer decide.
   */
  private ThreatDecision decide(
      io.qwenbridge.threat.model.ThreatSeverity severity, ThreatRiskLevel riskLevel) {
    if (riskLevel == ThreatRiskLevel.CRITICAL) {
      return ThreatDecision.BLOCK;
    }

    if (riskLevel == ThreatRiskLevel.HIGH) {
      return ThreatDecision.BLOCK;
    }

    if (riskLevel == ThreatRiskLevel.MEDIUM) {
      return ThreatDecision.REVIEW;
    }

    return decisionEngine.decide(severity);
  }
}

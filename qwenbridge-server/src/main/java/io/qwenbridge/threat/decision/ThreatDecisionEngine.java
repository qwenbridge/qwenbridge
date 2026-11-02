package io.qwenbridge.threat.decision;

import io.qwenbridge.threat.model.ThreatDecision;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ThreatDecisionEngine {

  public ThreatDecision decide(double score) {
    if (score >= 0.70) {
      return ThreatDecision.BLOCK;
    }

    if (score >= 0.30) {
      return ThreatDecision.REVIEW;
    }

    return ThreatDecision.ALLOW;
  }

  /**
   * Decision for user input from the strictest finding severity (QB-068), using the Core default
   * {@link io.qwenbridge.core.threat.ThreatPolicy}: HIGH and CRITICAL block, MEDIUM needs review,
   * LOW is allowed. Severity is categorical; no hand-set score decides.
   */
  public ThreatDecision decide(io.qwenbridge.threat.model.ThreatSeverity severity) {
    if (severity == null) {
      return ThreatDecision.ALLOW;
    }
    io.qwenbridge.core.threat.ThreatAction action =
        io.qwenbridge.core.threat.ThreatPolicy.defaults()
            .actionFor(
                io.qwenbridge.core.threat.TrustZone.USER_INPUT,
                io.qwenbridge.core.threat.ThreatSeverity.valueOf(severity.name()));
    return switch (action) {
      case ALLOW -> ThreatDecision.ALLOW;
      case REVIEW -> ThreatDecision.REVIEW;
      case QUARANTINE, BLOCK -> ThreatDecision.BLOCK;
    };
  }
}

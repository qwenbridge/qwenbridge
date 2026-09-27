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
}

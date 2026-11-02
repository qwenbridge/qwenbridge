package io.qwenbridge.threat.scoring;

import io.qwenbridge.threat.model.ThreatFinding;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Comparator;
import java.util.List;

@ApplicationScoped
public class ThreatScoringService {

  public double score(List<ThreatFinding> findings) {
    if (findings == null || findings.isEmpty()) {
      return 0.0;
    }

    return findings.stream().map(ThreatFinding::score).max(Comparator.naturalOrder()).orElse(0.0);
  }

  /** The strictest severity among the findings, or null when there are none (QB-068). */
  public io.qwenbridge.threat.model.ThreatSeverity maxSeverity(List<ThreatFinding> findings) {
    if (findings == null || findings.isEmpty()) {
      return null;
    }
    return findings.stream()
        .map(ThreatFinding::severity)
        .max(Comparator.naturalOrder())
        .orElse(null);
  }
}

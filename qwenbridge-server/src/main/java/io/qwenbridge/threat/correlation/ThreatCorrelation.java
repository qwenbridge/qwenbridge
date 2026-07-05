package io.qwenbridge.threat.correlation;

import io.qwenbridge.threat.model.ThreatType;
import java.util.List;

public record ThreatCorrelation(
    String id,
    List<ThreatType> types,
    double scoreBoost,
    ThreatRiskLevel riskLevel,
    String reason) {
  public ThreatCorrelation {
    id = id == null ? "" : id;
    types = types == null ? List.of() : List.copyOf(types);
    scoreBoost = Math.max(0.0, Math.min(1.0, scoreBoost));
    riskLevel = riskLevel == null ? ThreatRiskLevel.NONE : riskLevel;
    reason = reason == null ? "" : reason;
  }
}

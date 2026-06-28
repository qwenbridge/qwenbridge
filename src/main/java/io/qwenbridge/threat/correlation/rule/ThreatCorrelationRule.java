package io.qwenbridge.threat.correlation.rule;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatType;

import java.util.List;

public record ThreatCorrelationRule(
        String id,
        List<ThreatType> types,
        double scoreBoost,
        ThreatRiskLevel riskLevel,
        String reason
) {
    public ThreatCorrelationRule {
        id = id == null ? "" : id;
        types = types == null ? List.of() : List.copyOf(types);
        scoreBoost = Math.max(0.0, Math.min(1.0, scoreBoost));
        riskLevel = riskLevel == null ? ThreatRiskLevel.NONE : riskLevel;
        reason = reason == null ? "" : reason;
    }

    public boolean matches(List<ThreatType> detectedTypes) {
        return detectedTypes != null && detectedTypes.containsAll(types);
    }
}

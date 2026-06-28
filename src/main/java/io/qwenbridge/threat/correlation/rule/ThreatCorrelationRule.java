package io.qwenbridge.threat.correlation.rule;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;

public record ThreatCorrelationRule(
        String id,
        ThreatCorrelationCondition when,
        double scoreBoost,
        ThreatRiskLevel riskLevel,
        String reason
) {
    public ThreatCorrelationRule {
        id = id == null ? "" : id;
        when = when == null
                ? new ThreatCorrelationCondition(null, null, null)
                : when;
        scoreBoost = Math.max(0.0, Math.min(1.0, scoreBoost));
        riskLevel = riskLevel == null ? ThreatRiskLevel.NONE : riskLevel;
        reason = reason == null ? "" : reason;
    }
}

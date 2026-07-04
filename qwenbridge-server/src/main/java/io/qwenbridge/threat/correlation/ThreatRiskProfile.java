package io.qwenbridge.threat.correlation;

import java.util.List;

public record ThreatRiskProfile(
        double baseScore,
        double correlatedScore,
        ThreatRiskLevel riskLevel,
        List<ThreatCorrelation> correlations
) {
    public ThreatRiskProfile {
        baseScore = clamp(baseScore);
        correlatedScore = clamp(correlatedScore);
        riskLevel = riskLevel == null ? ThreatRiskLevel.NONE : riskLevel;
        correlations = correlations == null ? List.of() : List.copyOf(correlations);
    }

    public static ThreatRiskProfile none() {
        return new ThreatRiskProfile(0.0, 0.0, ThreatRiskLevel.NONE, List.of());
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

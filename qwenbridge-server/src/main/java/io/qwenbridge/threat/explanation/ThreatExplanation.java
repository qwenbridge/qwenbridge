package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatDecision;

import java.util.List;

public record ThreatExplanation(
        ThreatRiskLevel riskLevel,
        ThreatDecision decision,
        List<ThreatExplanationItem> items,
        List<String> matchedCorrelationRules
) {
    public ThreatExplanation {
        riskLevel = riskLevel == null ? ThreatRiskLevel.NONE : riskLevel;
        decision = decision == null ? ThreatDecision.ALLOW : decision;
        items = items == null ? List.of() : List.copyOf(items);
        matchedCorrelationRules = matchedCorrelationRules == null
                ? List.of()
                : List.copyOf(matchedCorrelationRules);
    }

    public static ThreatExplanation none() {
        return new ThreatExplanation(
                ThreatRiskLevel.NONE,
                ThreatDecision.ALLOW,
                List.of(),
                List.of()
        );
    }
}

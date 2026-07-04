package io.qwenbridge.threat.correlation;

import io.qwenbridge.threat.correlation.evaluator.ThreatCorrelationEvaluator;
import io.qwenbridge.threat.correlation.rule.ThreatCorrelationRule;
import io.qwenbridge.threat.correlation.rule.ThreatCorrelationRuleLoader;
import io.qwenbridge.threat.model.ThreatFinding;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class DefaultThreatCorrelationService implements ThreatCorrelationService {

    private final List<ThreatCorrelationRule> rules;
    private final ThreatCorrelationEvaluator evaluator;

    public DefaultThreatCorrelationService(
            ThreatCorrelationRuleLoader ruleLoader,
            ThreatCorrelationEvaluator evaluator
    ) {
        this.rules = ruleLoader.load("threat-rules/correlation.yml");
        this.evaluator = evaluator;
    }

    @Override
    public ThreatRiskProfile correlate(List<ThreatFinding> findings) {
        List<ThreatFinding> safeFindings = findings == null ? List.of() : List.copyOf(findings);

        if (safeFindings.isEmpty()) {
            return ThreatRiskProfile.none();
        }

        double baseScore = safeFindings.stream()
                .map(ThreatFinding::score)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        List<ThreatCorrelation> correlations = rules.stream()
                .filter(rule -> evaluator.matches(rule, safeFindings))
                .map(rule -> new ThreatCorrelation(
                        rule.id(),
                        rule.when().allOf(),
                        rule.scoreBoost(),
                        rule.riskLevel(),
                        rule.reason()
                ))
                .toList();

        double boost = correlations.stream()
                .map(ThreatCorrelation::scoreBoost)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        double correlatedScore = clamp(baseScore + boost);

        ThreatRiskLevel riskLevel = correlations.stream()
                .map(ThreatCorrelation::riskLevel)
                .max(Comparator.comparingInt(Enum::ordinal))
                .orElse(toRiskLevel(correlatedScore));

        return new ThreatRiskProfile(
                baseScore,
                correlatedScore,
                riskLevel,
                correlations
        );
    }

    private ThreatRiskLevel toRiskLevel(double score) {
        if (score >= 0.90) {
            return ThreatRiskLevel.CRITICAL;
        }

        if (score >= 0.70) {
            return ThreatRiskLevel.HIGH;
        }

        if (score >= 0.30) {
            return ThreatRiskLevel.MEDIUM;
        }

        if (score > 0.0) {
            return ThreatRiskLevel.LOW;
        }

        return ThreatRiskLevel.NONE;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

package io.qwenbridge.threat.correlation.evaluator;

import io.qwenbridge.threat.correlation.rule.ThreatCorrelationCondition;
import io.qwenbridge.threat.correlation.rule.ThreatCorrelationRule;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class DefaultThreatCorrelationEvaluator implements ThreatCorrelationEvaluator {

    @Override
    public boolean matches(ThreatCorrelationRule rule, List<ThreatFinding> findings) {
        if (rule == null || findings == null || findings.isEmpty()) {
            return false;
        }

        ThreatCorrelationCondition condition = rule.when();

        Set<ThreatType> detectedTypes = findings.stream()
                .map(ThreatFinding::type)
                .collect(Collectors.toSet());

        boolean allOfMatches = condition.allOf().isEmpty()
                || detectedTypes.containsAll(condition.allOf());

        boolean anyOfMatches = condition.anyOf().isEmpty()
                || condition.anyOf().stream().anyMatch(detectedTypes::contains);

        boolean noneOfMatches = condition.noneOf().stream()
                .noneMatch(detectedTypes::contains);

        return allOfMatches && anyOfMatches && noneOfMatches;
    }
}

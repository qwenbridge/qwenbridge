package io.qwenbridge.threat.detector.support;

import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.rule.ThreatPatternRule;

import java.util.List;

public abstract class PatternBasedThreatDetector implements ThreatDetector {

    private final List<ThreatPatternRule> rules;

    protected PatternBasedThreatDetector(List<ThreatPatternRule> rules) {
        this.rules = rules == null ? List.of() : List.copyOf(rules);
    }

    @Override
    public List<ThreatFinding> detect(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }

        return rules.stream()
                .filter(rule -> rule.matches(input))
                .map(rule -> rule.toFinding(name()))
                .toList();
    }
}

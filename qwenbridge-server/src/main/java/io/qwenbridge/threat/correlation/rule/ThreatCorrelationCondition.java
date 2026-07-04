package io.qwenbridge.threat.correlation.rule;

import io.qwenbridge.threat.model.ThreatType;

import java.util.List;

public record ThreatCorrelationCondition(
        List<ThreatType> allOf,
        List<ThreatType> anyOf,
        List<ThreatType> noneOf
) {
    public ThreatCorrelationCondition {
        allOf = allOf == null ? List.of() : List.copyOf(allOf);
        anyOf = anyOf == null ? List.of() : List.copyOf(anyOf);
        noneOf = noneOf == null ? List.of() : List.copyOf(noneOf);
    }
}

package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.model.ThreatAnalysis;

public interface ThreatExplanationBuilder {
    ThreatExplanation build(ThreatAnalysis analysis);
}

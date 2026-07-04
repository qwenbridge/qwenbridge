package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;

public record ThreatExplanationItem(
        ThreatType type,
        ThreatSeverity severity,
        String message,
        String source
) {
    public ThreatExplanationItem {
        message = message == null ? "" : message;
        source = source == null ? "" : source;
    }
}

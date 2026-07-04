package io.qwenbridge.threat.model;

public record ThreatFinding(
        ThreatType type,
        ThreatSeverity severity,
        double score,
        double confidence,
        String detector,
        String evidence,
        String reason
) {
    public ThreatFinding {
        type = type == null ? ThreatType.UNKNOWN : type;
        severity = severity == null ? ThreatSeverity.LOW : severity;
        score = clamp(score);
        confidence = clamp(confidence);
        detector = safe(detector);
        evidence = safe(evidence);
        reason = safe(reason);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}

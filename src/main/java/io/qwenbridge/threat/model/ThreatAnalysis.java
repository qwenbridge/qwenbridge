package io.qwenbridge.threat.model;

import java.util.Comparator;
import java.util.List;

public record ThreatAnalysis(
        boolean safe,
        double score,
        ThreatDecision decision,
        List<ThreatFinding> findings
) {
    public ThreatAnalysis {
        score = clamp(score);
        decision = decision == null ? ThreatDecision.ALLOW : decision;
        findings = findings == null ? List.of() : List.copyOf(findings);
        safe = decision == ThreatDecision.ALLOW;
    }

    public static ThreatAnalysis allow() {
        return new ThreatAnalysis(true, 0.0, ThreatDecision.ALLOW, List.of());
    }

    public static ThreatAnalysis from(List<ThreatFinding> findings, ThreatDecision decision) {
        List<ThreatFinding> safeFindings = findings == null ? List.of() : List.copyOf(findings);
        double maxScore = safeFindings.stream()
                .map(ThreatFinding::score)
                .max(Comparator.naturalOrder())
                .orElse(0.0);

        return new ThreatAnalysis(decision == ThreatDecision.ALLOW, maxScore, decision, safeFindings);
    }

    public boolean blocked() {
        return decision == ThreatDecision.BLOCK;
    }

    public boolean reviewRequired() {
        return decision == ThreatDecision.REVIEW;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}

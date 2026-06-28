package io.qwenbridge.threat.model;

import io.qwenbridge.threat.correlation.ThreatRiskProfile;
import io.qwenbridge.threat.explanation.ThreatExplanation;

import java.util.Comparator;
import java.util.List;

public record ThreatAnalysis(
        boolean safe,
        double score,
        ThreatDecision decision,
        List<ThreatFinding> findings,
        ThreatRiskProfile riskProfile,
        ThreatExplanation explanation
) {
    public ThreatAnalysis {
        score = clamp(score);
        decision = decision == null ? ThreatDecision.ALLOW : decision;
        findings = findings == null ? List.of() : List.copyOf(findings);
        riskProfile = riskProfile == null ? ThreatRiskProfile.none() : riskProfile;
        explanation = explanation == null ? ThreatExplanation.none() : explanation;
        safe = decision == ThreatDecision.ALLOW;
    }

    public static ThreatAnalysis allow() {
        return new ThreatAnalysis(true, 0.0, ThreatDecision.ALLOW, List.of(), ThreatRiskProfile.none(), ThreatExplanation.none());
    }

    public static ThreatAnalysis from(List<ThreatFinding> findings, ThreatDecision decision) {
        return from(findings, decision, ThreatRiskProfile.none(), ThreatExplanation.none());
    }

    public static ThreatAnalysis from(
            List<ThreatFinding> findings,
            ThreatDecision decision,
            ThreatRiskProfile riskProfile
    ) {
        return from(findings, decision, riskProfile, ThreatExplanation.none());
    }

    public static ThreatAnalysis from(
            List<ThreatFinding> findings,
            ThreatDecision decision,
            ThreatRiskProfile riskProfile,
            ThreatExplanation explanation
    ) {
        List<ThreatFinding> safeFindings = findings == null ? List.of() : List.copyOf(findings);
        double score = riskProfile == null
                ? safeFindings.stream().map(ThreatFinding::score).max(Comparator.naturalOrder()).orElse(0.0)
                : riskProfile.correlatedScore();

        return new ThreatAnalysis(decision == ThreatDecision.ALLOW, score, decision, safeFindings, riskProfile, explanation);
    }

    public ThreatAnalysis withExplanation(ThreatExplanation explanation) {
        return new ThreatAnalysis(safe, score, decision, findings, riskProfile, explanation);
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

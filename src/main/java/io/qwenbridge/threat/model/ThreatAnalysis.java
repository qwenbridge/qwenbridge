package io.qwenbridge.threat.model;

import lombok.Builder;

import io.qwenbridge.threat.correlation.ThreatRiskProfile;
import io.qwenbridge.threat.explanation.ThreatExplanation;

import java.util.Comparator;
import java.util.List;

@Builder
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
        return ThreatAnalysis.builder()
                .safe(true)
                .score(0.0)
                .decision(ThreatDecision.ALLOW)
                .findings(List.of())
                .riskProfile(ThreatRiskProfile.none())
                .explanation(ThreatExplanation.none())
                .build();
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

        return ThreatAnalysis.builder()
                .safe(decision == ThreatDecision.ALLOW)
                .score(score)
                .decision(decision)
                .findings(safeFindings)
                .riskProfile(riskProfile)
                .explanation(explanation)
                .build();
    }

    public ThreatAnalysis withExplanation(ThreatExplanation explanation) {
        return ThreatAnalysis.builder()
                .safe(safe)
                .score(score)
                .decision(decision)
                .findings(findings)
                .riskProfile(riskProfile)
                .explanation(explanation)
                .build();
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

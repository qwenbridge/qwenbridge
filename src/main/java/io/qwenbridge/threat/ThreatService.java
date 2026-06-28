package io.qwenbridge.threat;

import io.qwenbridge.threat.decision.ThreatDecisionEngine;
import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.detector.ThreatDetectorRegistry;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatDecision;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.scoring.ThreatScoringService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ThreatService {

    private final ThreatDetectorRegistry detectorRegistry;
    private final ThreatScoringService scoringService;
    private final ThreatDecisionEngine decisionEngine;

    public ThreatService(
            ThreatDetectorRegistry detectorRegistry,
            ThreatScoringService scoringService,
            ThreatDecisionEngine decisionEngine
    ) {
        this.detectorRegistry = detectorRegistry;
        this.scoringService = scoringService;
        this.decisionEngine = decisionEngine;
    }

    public ThreatResult analyze(String query) {
        ThreatAnalysis analysis = analyzeDetailed(query);

        return new ThreatResult(
                analysis.safe(),
                analysis.findings().stream()
                        .map(ThreatFinding::reason)
                        .filter(reason -> reason != null && !reason.isBlank())
                        .toList()
        );
    }

    public ThreatAnalysis analyzeDetailed(String query) {
        String safeQuery = query == null ? "" : query;
        List<ThreatFinding> findings = new ArrayList<>();

        for (ThreatDetector detector : detectorRegistry.detectors()) {
            List<ThreatFinding> detectorFindings = detector.detect(safeQuery);

            if (detectorFindings != null && !detectorFindings.isEmpty()) {
                findings.addAll(detectorFindings);
            }
        }

        double score = scoringService.score(findings);
        ThreatDecision decision = decisionEngine.decide(score);

        return ThreatAnalysis.from(findings, decision);
    }
}

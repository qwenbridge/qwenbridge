package io.qwenbridge.threat.scoring;

import io.qwenbridge.threat.model.ThreatFinding;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class ThreatScoringService {

    public double score(List<ThreatFinding> findings) {
        if (findings == null || findings.isEmpty()) {
            return 0.0;
        }

        return findings.stream()
                .map(ThreatFinding::score)
                .max(Comparator.naturalOrder())
                .orElse(0.0);
    }
}

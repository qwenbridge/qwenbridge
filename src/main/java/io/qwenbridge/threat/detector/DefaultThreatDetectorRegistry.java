package io.qwenbridge.threat.detector;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DefaultThreatDetectorRegistry implements ThreatDetectorRegistry {

    private final List<ThreatDetector> detectors;

    public DefaultThreatDetectorRegistry(List<ThreatDetector> detectors) {
        this.detectors = detectors == null
                ? List.of()
                : detectors.stream()
                        .sorted(Comparator.comparingInt(ThreatDetector::order)
                                .thenComparing(ThreatDetector::name))
                        .toList();
    }

    @Override
    public List<ThreatDetector> detectors() {
        return detectors;
    }
}

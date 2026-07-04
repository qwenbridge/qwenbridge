package io.qwenbridge.threat.detector;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DefaultThreatDetectorRegistry implements ThreatDetectorRegistry {

    private final List<ThreatDetector> detectors;

    @Override
    public List<ThreatDetector> detectors() {
        return detectors;
    }
}

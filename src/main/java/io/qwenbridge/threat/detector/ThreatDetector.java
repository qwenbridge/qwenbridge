package io.qwenbridge.threat.detector;

import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatType;

import java.util.List;

public interface ThreatDetector {

    String name();

    ThreatType type();

    default int order() {
        return 100;
    }

    List<ThreatFinding> detect(String input);
}

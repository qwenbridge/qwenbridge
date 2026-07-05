package io.qwenbridge.threat.detector;

import java.util.List;

public interface ThreatDetectorRegistry {
  List<ThreatDetector> detectors();
}

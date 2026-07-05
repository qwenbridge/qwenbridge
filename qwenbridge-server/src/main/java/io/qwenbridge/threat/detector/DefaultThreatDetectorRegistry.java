package io.qwenbridge.threat.detector;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultThreatDetectorRegistry implements ThreatDetectorRegistry {

  private final List<ThreatDetector> detectors;

  @Override
  public List<ThreatDetector> detectors() {
    return detectors;
  }
}

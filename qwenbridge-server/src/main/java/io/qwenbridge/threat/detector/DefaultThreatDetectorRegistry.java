package io.qwenbridge.threat.detector;

import io.quarkus.arc.All;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class DefaultThreatDetectorRegistry implements ThreatDetectorRegistry {

  private final List<ThreatDetector> detectors;

  @Inject
  public DefaultThreatDetectorRegistry(@All List<ThreatDetector> detectors) {
    this.detectors = detectors;
  }

  @Override
  public List<ThreatDetector> detectors() {
    return detectors;
  }
}

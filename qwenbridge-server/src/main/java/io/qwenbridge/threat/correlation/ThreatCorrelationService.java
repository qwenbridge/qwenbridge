package io.qwenbridge.threat.correlation;

import io.qwenbridge.threat.model.ThreatFinding;
import java.util.List;

public interface ThreatCorrelationService {
  ThreatRiskProfile correlate(List<ThreatFinding> findings);
}

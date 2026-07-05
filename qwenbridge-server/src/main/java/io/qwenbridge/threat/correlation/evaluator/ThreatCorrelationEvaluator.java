package io.qwenbridge.threat.correlation.evaluator;

import io.qwenbridge.threat.correlation.rule.ThreatCorrelationRule;
import io.qwenbridge.threat.model.ThreatFinding;
import java.util.List;

public interface ThreatCorrelationEvaluator {
  boolean matches(ThreatCorrelationRule rule, List<ThreatFinding> findings);
}

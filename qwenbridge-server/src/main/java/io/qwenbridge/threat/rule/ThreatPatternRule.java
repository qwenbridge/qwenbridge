package io.qwenbridge.threat.rule;

import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import java.util.regex.Pattern;

public record ThreatPatternRule(
    String id,
    ThreatType type,
    Pattern pattern,
    ThreatSeverity severity,
    double score,
    double confidence,
    String evidence,
    String reason) {
  public boolean matches(String input) {
    return input != null && pattern.matcher(input).find();
  }

  public ThreatFinding toFinding(String detector) {
    return new ThreatFinding(type, severity, score, confidence, detector, evidence, reason);
  }
}

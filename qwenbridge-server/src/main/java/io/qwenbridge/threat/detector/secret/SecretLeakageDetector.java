package io.qwenbridge.threat.detector.secret;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class SecretLeakageDetector extends PatternBasedThreatDetector {

  public SecretLeakageDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/secret-leakage.yml", ThreatType.SECRET_LEAKAGE));
  }

  @Override
  public String name() {
    return "secret-leakage-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.SECRET_LEAKAGE;
  }

  @Override
  public int order() {
    return 100;
  }
}

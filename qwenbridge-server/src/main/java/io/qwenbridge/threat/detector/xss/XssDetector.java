package io.qwenbridge.threat.detector.xss;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class XssDetector extends PatternBasedThreatDetector {

  public XssDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/xss.yml", ThreatType.XSS));
  }

  @Override
  public String name() {
    return "xss-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.XSS;
  }

  @Override
  public int order() {
    return 20;
  }
}

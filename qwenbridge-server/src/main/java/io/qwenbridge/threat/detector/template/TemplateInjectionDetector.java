package io.qwenbridge.threat.detector.template;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class TemplateInjectionDetector extends PatternBasedThreatDetector {

  public TemplateInjectionDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/template-injection.yml", ThreatType.TEMPLATE_INJECTION));
  }

  @Override
  public String name() {
    return "template-injection-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.TEMPLATE_INJECTION;
  }

  @Override
  public int order() {
    return 60;
  }
}

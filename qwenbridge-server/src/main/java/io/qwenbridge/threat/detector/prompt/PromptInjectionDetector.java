package io.qwenbridge.threat.detector.prompt;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class PromptInjectionDetector extends PatternBasedThreatDetector {

  public PromptInjectionDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/prompt-injection.yml", ThreatType.PROMPT_INJECTION));
  }

  @Override
  public String name() {
    return "prompt-injection-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.PROMPT_INJECTION;
  }

  @Override
  public int order() {
    return 30;
  }
}

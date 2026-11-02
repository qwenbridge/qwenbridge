package io.qwenbridge.threat.detector.command;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import jakarta.inject.Singleton;

@Singleton
public class CommandInjectionDetector extends PatternBasedThreatDetector {

  public CommandInjectionDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/command-injection.yml", ThreatType.COMMAND_INJECTION));
  }

  @Override
  public String name() {
    return "command-injection-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.COMMAND_INJECTION;
  }

  @Override
  public int order() {
    return 40;
  }
}

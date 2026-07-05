package io.qwenbridge.threat.detector.nosql;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class NoSqlInjectionDetector extends PatternBasedThreatDetector {

  public NoSqlInjectionDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/nosql-injection.yml", ThreatType.NOSQL_INJECTION));
  }

  @Override
  public String name() {
    return "nosql-injection-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.NOSQL_INJECTION;
  }

  @Override
  public int order() {
    return 70;
  }
}

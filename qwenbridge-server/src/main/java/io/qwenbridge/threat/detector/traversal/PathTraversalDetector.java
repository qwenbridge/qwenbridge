package io.qwenbridge.threat.detector.traversal;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class PathTraversalDetector extends PatternBasedThreatDetector {

  public PathTraversalDetector(ThreatRuleLoader ruleLoader) {
    super(ruleLoader.load("threat-rules/path-traversal.yml", ThreatType.PATH_TRAVERSAL));
  }

  @Override
  public String name() {
    return "path-traversal-detector";
  }

  @Override
  public ThreatType type() {
    return ThreatType.PATH_TRAVERSAL;
  }

  @Override
  public int order() {
    return 50;
  }
}

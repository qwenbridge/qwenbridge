package io.qwenbridge.threat.detector.jailbreak;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class JailbreakDetector extends PatternBasedThreatDetector {

    public JailbreakDetector(ThreatRuleLoader ruleLoader) {
        super(ruleLoader.load("threat-rules/jailbreak.yml", ThreatType.JAILBREAK));
    }

    @Override
    public String name() {
        return "jailbreak-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.JAILBREAK;
    }

    @Override
    public int order() {
        return 110;
    }
}

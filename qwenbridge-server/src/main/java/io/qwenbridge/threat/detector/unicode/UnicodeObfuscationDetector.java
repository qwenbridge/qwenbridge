package io.qwenbridge.threat.detector.unicode;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class UnicodeObfuscationDetector extends PatternBasedThreatDetector {

    public UnicodeObfuscationDetector(ThreatRuleLoader ruleLoader) {
        super(ruleLoader.load("threat-rules/unicode-obfuscation.yml", ThreatType.UNICODE_OBFUSCATION));
    }

    @Override
    public String name() {
        return "unicode-obfuscation-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.UNICODE_OBFUSCATION;
    }

    @Override
    public int order() {
        return 120;
    }
}

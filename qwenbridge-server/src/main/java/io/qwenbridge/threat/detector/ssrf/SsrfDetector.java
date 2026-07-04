package io.qwenbridge.threat.detector.ssrf;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class SsrfDetector extends PatternBasedThreatDetector {

    public SsrfDetector(ThreatRuleLoader ruleLoader) {
        super(ruleLoader.load("threat-rules/ssrf.yml", ThreatType.SSRF));
    }

    @Override
    public String name() {
        return "ssrf-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.SSRF;
    }

    @Override
    public int order() {
        return 90;
    }
}

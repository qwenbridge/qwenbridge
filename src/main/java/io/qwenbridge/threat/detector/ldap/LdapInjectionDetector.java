package io.qwenbridge.threat.detector.ldap;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class LdapInjectionDetector extends PatternBasedThreatDetector {

    public LdapInjectionDetector(ThreatRuleLoader ruleLoader) {
        super(ruleLoader.load("threat-rules/ldap-injection.yml", ThreatType.LDAP_INJECTION));
    }

    @Override
    public String name() {
        return "ldap-injection-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.LDAP_INJECTION;
    }

    @Override
    public int order() {
        return 80;
    }
}

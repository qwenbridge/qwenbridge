package io.qwenbridge.threat.detector.sql;

import io.qwenbridge.threat.detector.support.PatternBasedThreatDetector;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.springframework.stereotype.Component;

@Component
public class SqlInjectionDetector extends PatternBasedThreatDetector {

    public SqlInjectionDetector(ThreatRuleLoader ruleLoader) {
        super(ruleLoader.load("threat-rules/sql.yml", ThreatType.SQL_INJECTION));
    }

    @Override
    public String name() {
        return "sql-injection-detector";
    }

    @Override
    public ThreatType type() {
        return ThreatType.SQL_INJECTION;
    }

    @Override
    public int order() {
        return 10;
    }
}

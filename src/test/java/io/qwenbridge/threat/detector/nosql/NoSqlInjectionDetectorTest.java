package io.qwenbridge.threat.detector.nosql;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NoSqlInjectionDetectorTest {

    private final NoSqlInjectionDetector detector =
            new NoSqlInjectionDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectDollarOperatorInjection() {
        var findings = detector.detect("{\"username\":{\"$ne\":null}}");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.NOSQL_INJECTION);
    }

    @Test
    void shouldDetectWhereJavascriptInjection() {
        var findings = detector.detect("{\"$where\":\"function(){ return this.password.length > 0 }\"}");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.NOSQL_INJECTION);
    }

    @Test
    void shouldDetectRegexWildcardBypass() {
        var findings = detector.detect("{\"name\":{\"$regex\":\".*\"}}");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.NOSQL_INJECTION);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("mongodb book for beginners");

        assertThat(findings).isEmpty();
    }
}

package io.qwenbridge.threat.detector.sql;

import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SqlInjectionDetectorTest {

    private final SqlInjectionDetector detector = new SqlInjectionDetector();

    @Test
    void shouldDetectUnionSelectAttack() {
        var findings = detector.detect("desk union select password from users");

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.SQL_INJECTION);
        assertThat(findings.getFirst().severity()).isEqualTo(ThreatSeverity.CRITICAL);
        assertThat(findings.getFirst().score()).isGreaterThanOrEqualTo(0.90);
    }

    @Test
    void shouldDetectBooleanBypassAttack() {
        var findings = detector.detect("' OR 1=1 --");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.SQL_INJECTION);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("wooden desk for home office");

        assertThat(findings).isEmpty();
    }
}

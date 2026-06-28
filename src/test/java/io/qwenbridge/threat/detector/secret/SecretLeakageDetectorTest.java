package io.qwenbridge.threat.detector.secret;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecretLeakageDetectorTest {

    private final SecretLeakageDetector detector =
            new SecretLeakageDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectAwsAccessKey() {
        var findings = detector.detect("AKIA1234567890ABCDEF");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.SECRET_LEAKAGE);
    }

    @Test
    void shouldDetectGithubToken() {
        var findings = detector.detect("ghp_1234567890abcdefghijklmnopqrstuvwxyz");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.SECRET_LEAKAGE);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("best password manager comparison");

        assertThat(findings).isEmpty();
    }
}

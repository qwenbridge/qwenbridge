package io.qwenbridge.threat.detector.ssrf;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SsrfDetectorTest {

    private final SsrfDetector detector =
            new SsrfDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectAwsMetadata() {
        var findings = detector.detect("http://169.254.169.254/latest/meta-data");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.SSRF);
    }

    @Test
    void shouldDetectPrivateNetwork() {
        var findings = detector.detect("http://192.168.1.100/admin");

        assertThat(findings).isNotEmpty();
    }

    @Test
    void shouldDetectDangerousScheme() {
        var findings = detector.detect("file:///etc/passwd");

        assertThat(findings).isNotEmpty();
    }

    @Test
    void shouldAllowPublicWebsite() {
        var findings = detector.detect("https://openai.com");

        assertThat(findings).isEmpty();
    }
}

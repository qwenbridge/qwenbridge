package io.qwenbridge.threat.detector.xss;

import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XssDetectorTest {

    private final XssDetector detector = new XssDetector();

    @Test
    void shouldDetectScriptTag() {
        var findings = detector.detect("<script>alert(1)</script>");

        assertThat(findings).hasSize(1);
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.XSS);
        assertThat(findings.getFirst().severity()).isEqualTo(ThreatSeverity.CRITICAL);
    }

    @Test
    void shouldDetectJavascriptUri() {
        var findings = detector.detect("<a href=\"javascript:alert(1)\">click</a>");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.XSS);
    }

    @Test
    void shouldDetectEventHandler() {
        var findings = detector.detect("<img src=x onerror=alert(1)>");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.XSS);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("red sofa with modern design");

        assertThat(findings).isEmpty();
    }
}

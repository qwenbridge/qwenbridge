package io.qwenbridge.threat.detector.command;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandInjectionDetectorTest {

    private final CommandInjectionDetector detector =
            new CommandInjectionDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectShellCommandChaining() {
        var findings = detector.detect("desk; cat /etc/passwd");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.COMMAND_INJECTION);
    }

    @Test
    void shouldDetectCommandSubstitution() {
        var findings = detector.detect("desk $(whoami)");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.COMMAND_INJECTION);
    }

    @Test
    void shouldDetectWindowsShellExecution() {
        var findings = detector.detect("cmd.exe /c whoami");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.COMMAND_INJECTION);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("best desk for home office");

        assertThat(findings).isEmpty();
    }
}

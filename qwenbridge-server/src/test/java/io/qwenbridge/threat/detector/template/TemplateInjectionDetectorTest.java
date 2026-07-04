package io.qwenbridge.threat.detector.template;

import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.rule.ThreatRuleLoader;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateInjectionDetectorTest {

    private final TemplateInjectionDetector detector =
            new TemplateInjectionDetector(new ThreatRuleLoader());

    @Test
    void shouldDetectJinjaExpression() {
        var findings = detector.detect("{{config.items()}}");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.TEMPLATE_INJECTION);
    }

    @Test
    void shouldDetectExpressionLanguage() {
        var findings = detector.detect("${7*7}");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.TEMPLATE_INJECTION);
    }

    @Test
    void shouldDetectFreemarkerDirective() {
        var findings = detector.detect("<#assign x=1>");

        assertThat(findings).isNotEmpty();
        assertThat(findings.getFirst().type()).isEqualTo(ThreatType.TEMPLATE_INJECTION);
    }

    @Test
    void shouldAllowSafeSearchQuery() {
        var findings = detector.detect("template for office desk invoice");

        assertThat(findings).isEmpty();
    }
}

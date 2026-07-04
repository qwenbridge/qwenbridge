package io.qwenbridge.threat.correlation;

import io.qwenbridge.threat.correlation.evaluator.DefaultThreatCorrelationEvaluator;
import io.qwenbridge.threat.correlation.rule.ThreatCorrelationRuleLoader;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultThreatCorrelationServiceTest {

    private final DefaultThreatCorrelationService service =
            new DefaultThreatCorrelationService(
                    new ThreatCorrelationRuleLoader(),
                    new DefaultThreatCorrelationEvaluator()
            );

    @Test
    void shouldReturnNoneProfileWhenThereAreNoFindings() {
        ThreatRiskProfile profile = service.correlate(List.of());

        assertThat(profile.baseScore()).isZero();
        assertThat(profile.correlatedScore()).isZero();
        assertThat(profile.riskLevel()).isEqualTo(ThreatRiskLevel.NONE);
        assertThat(profile.correlations()).isEmpty();
    }

    @Test
    void shouldCorrelatePromptInjectionAndJailbreak() {
        ThreatRiskProfile profile = service.correlate(List.of(
                finding(ThreatType.PROMPT_INJECTION, 0.78),
                finding(ThreatType.JAILBREAK, 0.80)
        ));

        assertThat(profile.baseScore()).isEqualTo(0.80);
        assertThat(profile.correlatedScore()).isEqualTo(0.98);
        assertThat(profile.riskLevel()).isEqualTo(ThreatRiskLevel.CRITICAL);
        assertThat(profile.correlations())
                .extracting(ThreatCorrelation::id)
                .contains("prompt-plus-jailbreak");
    }

    @Test
    void shouldFallbackToScoreBasedRiskWhenNoCorrelationMatches() {
        ThreatRiskProfile profile = service.correlate(List.of(
                finding(ThreatType.SQL_INJECTION, 0.85)
        ));

        assertThat(profile.correlations()).isEmpty();
        assertThat(profile.correlatedScore()).isEqualTo(0.85);
        assertThat(profile.riskLevel()).isEqualTo(ThreatRiskLevel.HIGH);
    }

    private ThreatFinding finding(ThreatType type, double score) {
        return new ThreatFinding(
                type,
                ThreatSeverity.HIGH,
                score,
                0.90,
                "test-detector",
                type.name(),
                type.name() + " detected"
        );
    }
}

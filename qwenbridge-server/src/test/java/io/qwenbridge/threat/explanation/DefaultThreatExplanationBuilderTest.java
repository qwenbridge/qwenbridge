package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.correlation.ThreatCorrelation;
import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.correlation.ThreatRiskProfile;
import io.qwenbridge.threat.model.ThreatAnalysis;
import io.qwenbridge.threat.model.ThreatDecision;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultThreatExplanationBuilderTest {

    private final DefaultThreatExplanationBuilder builder =
            new DefaultThreatExplanationBuilder();

    @Test
    void shouldBuildExplanationFromThreatAnalysis() {
        ThreatFinding finding = new ThreatFinding(
                ThreatType.SQL_INJECTION,
                ThreatSeverity.HIGH,
                0.95,
                0.90,
                "sql-injection-detector",
                "union select",
                "SQL Injection pattern matched"
        );

        ThreatCorrelation correlation = new ThreatCorrelation(
                "sql_prompt_attack",
                List.of(ThreatType.SQL_INJECTION, ThreatType.PROMPT_INJECTION),
                0.20,
                ThreatRiskLevel.CRITICAL,
                "SQL injection combined with prompt injection."
        );

        ThreatRiskProfile profile = new ThreatRiskProfile(
                0.95,
                1.0,
                ThreatRiskLevel.CRITICAL,
                List.of(correlation)
        );

        ThreatAnalysis analysis = ThreatAnalysis.from(
                List.of(finding),
                ThreatDecision.BLOCK,
                profile
        );

        ThreatExplanation explanation = builder.build(analysis);

        assertThat(explanation.riskLevel()).isEqualTo(ThreatRiskLevel.CRITICAL);
        assertThat(explanation.decision()).isEqualTo(ThreatDecision.BLOCK);
        assertThat(explanation.items()).hasSize(1);
        assertThat(explanation.items().getFirst().message())
                .isEqualTo("SQL Injection pattern matched");
        assertThat(explanation.items().getFirst().source())
                .isEqualTo("sql-injection-detector");
        assertThat(explanation.matchedCorrelationRules())
                .containsExactly("sql_prompt_attack");
    }

    @Test
    void shouldReturnEmptyExplanationForNullAnalysis() {
        ThreatExplanation explanation = builder.build(null);

        assertThat(explanation.riskLevel()).isEqualTo(ThreatRiskLevel.NONE);
        assertThat(explanation.decision()).isEqualTo(ThreatDecision.ALLOW);
        assertThat(explanation.items()).isEmpty();
        assertThat(explanation.matchedCorrelationRules()).isEmpty();
    }
}

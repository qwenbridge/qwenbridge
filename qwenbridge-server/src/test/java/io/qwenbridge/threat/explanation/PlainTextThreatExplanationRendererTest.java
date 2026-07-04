package io.qwenbridge.threat.explanation;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatDecision;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlainTextThreatExplanationRendererTest {

    private final PlainTextThreatExplanationRenderer renderer =
            new PlainTextThreatExplanationRenderer();

    @Test
    void shouldRenderPlainTextExplanation() {
        ThreatExplanation explanation = new ThreatExplanation(
                ThreatRiskLevel.CRITICAL,
                ThreatDecision.BLOCK,
                List.of(
                        new ThreatExplanationItem(
                                ThreatType.SQL_INJECTION,
                                ThreatSeverity.HIGH,
                                "SQL Injection pattern matched",
                                "sql-injection-detector"
                        )
                ),
                List.of("sql_prompt_attack")
        );

        String rendered = renderer.render(explanation);

        assertThat(rendered).contains("Threat Explanation");
        assertThat(rendered).contains("SQL Injection pattern matched");
        assertThat(rendered).contains("Correlation rule \"sql_prompt_attack\" matched");
        assertThat(rendered).contains("Final Risk:");
        assertThat(rendered).contains("CRITICAL");
        assertThat(rendered).contains("Decision:");
        assertThat(rendered).contains("BLOCK");
    }

    @Test
    void shouldRenderEmptyExplanation() {
        String rendered = renderer.render(null);

        assertThat(rendered).contains("Threat Explanation");
        assertThat(rendered).contains("Final Risk:");
        assertThat(rendered).contains("NONE");
        assertThat(rendered).contains("Decision:");
        assertThat(rendered).contains("ALLOW");
    }
}

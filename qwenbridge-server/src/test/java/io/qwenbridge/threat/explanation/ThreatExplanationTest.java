package io.qwenbridge.threat.explanation;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.threat.correlation.ThreatRiskLevel;
import io.qwenbridge.threat.model.ThreatDecision;
import org.junit.jupiter.api.Test;

class ThreatExplanationTest {

  @Test
  void shouldCreateEmptyExplanation() {
    ThreatExplanation explanation = ThreatExplanation.none();

    assertThat(explanation.riskLevel()).isEqualTo(ThreatRiskLevel.NONE);
    assertThat(explanation.decision()).isEqualTo(ThreatDecision.ALLOW);
    assertThat(explanation.items()).isEmpty();
    assertThat(explanation.matchedCorrelationRules()).isEmpty();
  }
}

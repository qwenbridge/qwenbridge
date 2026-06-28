package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.threat.ThreatResult;
import io.qwenbridge.threat.ThreatService;
import io.qwenbridge.threat.decision.ThreatDecisionEngine;
import io.qwenbridge.threat.detector.DefaultThreatDetectorRegistry;
import io.qwenbridge.threat.detector.ThreatDetector;
import io.qwenbridge.threat.model.ThreatFinding;
import io.qwenbridge.threat.model.ThreatSeverity;
import io.qwenbridge.threat.model.ThreatType;
import io.qwenbridge.threat.scoring.ThreatScoringService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatStepTest {

    @Test
    void shouldDetectThreatUsingDetectorRegistry() {
        ExecutionContext context =
                new ExecutionContext("desk union select password from users");

        ThreatStep step = new ThreatStep(threatServiceWith(new FakeSqlInjectionDetector()));

        ThreatResult result = step.execute(context);

        assertThat(result.safe()).isFalse();
        assertThat(result.reasons()).contains("SQL injection pattern detected.");
    }

    @Test
    void shouldAllowSafeQueryWhenDetectorsReturnNoFindings() {
        ExecutionContext context = new ExecutionContext("desk");

        ThreatStep step = new ThreatStep(threatServiceWith(new FakeSqlInjectionDetector()));

        ThreatResult result = step.execute(context);

        assertThat(result.safe()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }

    private static ThreatService threatServiceWith(ThreatDetector detector) {
        return new ThreatService(
                new DefaultThreatDetectorRegistry(List.of(detector)),
                new ThreatScoringService(),
                new ThreatDecisionEngine()
        );
    }

    private static class FakeSqlInjectionDetector implements ThreatDetector {

        @Override
        public String name() {
            return "fake-sql-injection-detector";
        }

        @Override
        public ThreatType type() {
            return ThreatType.SQL_INJECTION;
        }

        @Override
        public List<ThreatFinding> detect(String input) {
            if (input != null && input.toLowerCase().contains("union select")) {
                return List.of(new ThreatFinding(
                        ThreatType.SQL_INJECTION,
                        ThreatSeverity.HIGH,
                        0.90,
                        0.95,
                        name(),
                        "union select",
                        "SQL injection pattern detected."
                ));
            }

            return List.of();
        }
    }
}

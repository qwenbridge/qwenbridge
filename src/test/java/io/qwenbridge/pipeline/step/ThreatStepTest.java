package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.threat.ThreatResult;
import io.qwenbridge.threat.ThreatService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ThreatStepTest {

    @Test
    void shouldDetectSqlInjection() {
        ExecutionContext context =
                new ExecutionContext("desk union select password from users");

        ThreatStep step = new ThreatStep(new ThreatService());

        ThreatResult result = step.execute(context);

        assertThat(result.safe()).isFalse();
        assertThat(result.reasons()).contains("SQL_INJECTION");
    }

    @Test
    void shouldAllowSafeQuery() {
        ExecutionContext context = new ExecutionContext("desk");

        ThreatStep step = new ThreatStep(new ThreatService());

        ThreatResult result = step.execute(context);

        assertThat(result.safe()).isTrue();
        assertThat(result.reasons()).isEmpty();
    }
}

package io.qwenbridge.pipeline;

import io.qwenbridge.pipeline.step.PipelineStep;
import io.qwenbridge.threat.ThreatResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEngineTest {

    @Test
    void shouldStopPipelineWhenThreatIsDetected() {
        AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

        PipelineStep<ThreatResult> threatStep = new PipelineStep<>() {
            public String name() { return "FakeThreatStep"; }
            public int order() { return 10; }
            public Class<ThreatResult> resultType() { return ThreatResult.class; }

            public ThreatResult execute(ExecutionContext context) {
                return ThreatResult.detected(List.of("SQL_INJECTION"));
            }
        };

        PipelineStep<String> nextStep = new PipelineStep<>() {
            public String name() { return "ShouldNotRunStep"; }
            public int order() { return 20; }
            public Class<String> resultType() { return String.class; }

            public String execute(ExecutionContext context) {
                secondStepExecuted.set(true);
                return "executed";
            }
        };

        PipelineEngine engine = new PipelineEngine(List.of(threatStep, nextStep));
        ExecutionContext context = new ExecutionContext("desk union select password from users");

        engine.execute(context);

        assertThat(secondStepExecuted).isFalse();
        assertThat(context.get(ThreatResult.class).safe()).isFalse();
        assertThat(context.get(ThreatResult.class).reasons()).contains("SQL_INJECTION");
    }

    @Test
    void shouldExecuteNextStepWhenNoThreatIsDetected() {
        AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

        PipelineStep<ThreatResult> threatStep = new PipelineStep<>() {
            public String name() { return "FakeThreatStep"; }
            public int order() { return 10; }
            public Class<ThreatResult> resultType() { return ThreatResult.class; }

            public ThreatResult execute(ExecutionContext context) {
                return ThreatResult.noThreat();
            }
        };

        PipelineStep<String> nextStep = new PipelineStep<>() {
            public String name() { return "ShouldRunStep"; }
            public int order() { return 20; }
            public Class<String> resultType() { return String.class; }

            public String execute(ExecutionContext context) {
                secondStepExecuted.set(true);
                return "executed";
            }
        };

        PipelineEngine engine = new PipelineEngine(List.of(threatStep, nextStep));
        ExecutionContext context = new ExecutionContext("desk");

        engine.execute(context);

        assertThat(secondStepExecuted).isTrue();
        assertThat(context.get(ThreatResult.class).safe()).isTrue();
        assertThat(context.get(String.class)).isEqualTo("executed");
    }
}

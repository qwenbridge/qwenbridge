package io.qwenbridge.pipeline;

import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.pipeline.step.PipelineStep;
import io.qwenbridge.threat.ThreatResult;
import org.junit.jupiter.api.Test;
import io.qwenbridge.event.noop.NoOpPipelineEventPublisher;
import io.qwenbridge.event.snapshot.PipelineContextSnapshotFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineEngineTest {

    @Test
    void shouldStopPipelineWhenThreatIsDetected() {
        AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

        PipelineStep<ThreatResult> threatStep = new PipelineStep<>() {

            @Override
            public PipelineStage stage() {
                return PipelineStage.THREAT;
            }

            @Override
            public String name() {
                return "FakeThreatStep";
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public Class<ThreatResult> resultType() {
                return ThreatResult.class;
            }

            @Override
            public ThreatResult execute(ExecutionContext context) {
                return ThreatResult.detected(List.of("SQL_INJECTION"));
            }
        };

        PipelineStep<String> nextStep = new PipelineStep<>() {

            @Override
            public PipelineStage stage() {
                return PipelineStage.SEARCH;
            }

            @Override
            public String name() {
                return "ShouldNotRunStep";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public Class<String> resultType() {
                return String.class;
            }

            @Override
            public String execute(ExecutionContext context) {
                secondStepExecuted.set(true);
                return "executed";
            }
        };

        PipelineEngine engine = new PipelineEngine(
                List.of(threatStep, nextStep),
                new NoOpPipelineEventPublisher(),
                new PipelineContextSnapshotFactory()
        );
        ExecutionContext context =
                new ExecutionContext("desk union select password from users");

        engine.execute(context);

        assertThat(secondStepExecuted).isFalse();
        assertThat(context.get(ThreatResult.class).safe()).isFalse();
        assertThat(context.get(ThreatResult.class).reasons())
                .contains("SQL_INJECTION");
    }

    @Test
    void shouldExecuteNextStepWhenNoThreatIsDetected() {

        AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

        PipelineStep<ThreatResult> threatStep = new PipelineStep<>() {

            @Override
            public PipelineStage stage() {
                return PipelineStage.THREAT;
            }

            @Override
            public String name() {
                return "FakeThreatStep";
            }

            @Override
            public int order() {
                return 10;
            }

            @Override
            public Class<ThreatResult> resultType() {
                return ThreatResult.class;
            }

            @Override
            public ThreatResult execute(ExecutionContext context) {
                return ThreatResult.noThreat();
            }
        };

        PipelineStep<String> nextStep = new PipelineStep<>() {

            @Override
            public PipelineStage stage() {
                return PipelineStage.SEARCH;
            }

            @Override
            public String name() {
                return "ShouldRunStep";
            }

            @Override
            public int order() {
                return 20;
            }

            @Override
            public Class<String> resultType() {
                return String.class;
            }

            @Override
            public String execute(ExecutionContext context) {
                secondStepExecuted.set(true);
                return "executed";
            }
        };

        PipelineEngine engine = new PipelineEngine(
                List.of(threatStep, nextStep),
                new NoOpPipelineEventPublisher(),
                new PipelineContextSnapshotFactory()
        );
        ExecutionContext context = new ExecutionContext("desk");

        engine.execute(context);

        assertThat(secondStepExecuted).isTrue();
        assertThat(context.get(ThreatResult.class).safe()).isTrue();
        assertThat(context.get(String.class)).isEqualTo("executed");
    }
}

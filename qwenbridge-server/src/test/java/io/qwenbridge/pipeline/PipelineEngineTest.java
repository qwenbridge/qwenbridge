package io.qwenbridge.pipeline;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.event.model.PipelineEvent;
import io.qwenbridge.event.model.PipelineEventType;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.event.noop.NoOpPipelineEventPublisher;
import io.qwenbridge.event.snapshot.PipelineContextSnapshotFactory;
import io.qwenbridge.event.spi.PipelineEventPublisher;
import io.qwenbridge.pipeline.step.PipelineStep;
import io.qwenbridge.threat.ThreatResult;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class PipelineEngineTest {

  @Test
  void shouldStopPipelineWhenThreatIsDetected() {
    AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

    PipelineStep<ThreatResult> threatStep =
        new PipelineStep<>() {

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

    PipelineStep<String> nextStep =
        new PipelineStep<>() {

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

    PipelineEngine engine =
        new PipelineEngine(
            List.of(threatStep, nextStep),
            new NoOpPipelineEventPublisher(),
            new PipelineContextSnapshotFactory());
    ExecutionContext context = new ExecutionContext("desk union select password from users");

    engine.execute(context);

    assertThat(secondStepExecuted).isFalse();
    assertThat(context.get(ThreatResult.class).safe()).isFalse();
    assertThat(context.get(ThreatResult.class).reasons()).contains("SQL_INJECTION");
  }

  @Test
  void shouldExecuteNextStepWhenNoThreatIsDetected() {

    AtomicBoolean secondStepExecuted = new AtomicBoolean(false);

    PipelineStep<ThreatResult> threatStep =
        new PipelineStep<>() {

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

    PipelineStep<String> nextStep =
        new PipelineStep<>() {

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

    PipelineEngine engine =
        new PipelineEngine(
            List.of(threatStep, nextStep),
            new NoOpPipelineEventPublisher(),
            new PipelineContextSnapshotFactory());
    ExecutionContext context = new ExecutionContext("desk");

    engine.execute(context);

    assertThat(secondStepExecuted).isTrue();
    assertThat(context.get(ThreatResult.class).safe()).isTrue();
    assertThat(context.get(String.class)).isEqualTo("executed");
  }

  @Test
  void shouldPublishPipelineStoppedOnlyOnceWhenPipelineIsBlocked() {
    List<PipelineEvent<?>> events = new ArrayList<>();

    PipelineEventPublisher publisher = events::add;

    PipelineStep<ThreatResult> threatStep =
        new PipelineStep<>() {

          @Override
          public PipelineStage stage() {
            return PipelineStage.THREAT;
          }

          @Override
          public String name() {
            return "BlockingThreatStep";
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

    PipelineStep<String> skippedStepOne = skippedStep("SkippedStepOne", 20);
    PipelineStep<String> skippedStepTwo = skippedStep("SkippedStepTwo", 30);

    PipelineEngine engine =
        new PipelineEngine(
            List.of(threatStep, skippedStepOne, skippedStepTwo),
            publisher,
            new PipelineContextSnapshotFactory());

    engine.execute(new ExecutionContext("request-1", "malicious query"));

    assertThat(events).filteredOn(event -> event.type() == PipelineEventType.STOPPED).hasSize(1);

    assertThat(events)
        .extracting(event -> event.type())
        .containsExactly(
            PipelineEventType.STARTED,
            PipelineEventType.STARTED,
            PipelineEventType.COMPLETED,
            PipelineEventType.STOPPED,
            PipelineEventType.COMPLETED);
  }

  private PipelineStep<String> skippedStep(String name, int order) {
    return new PipelineStep<>() {

      @Override
      public PipelineStage stage() {
        return PipelineStage.SEARCH;
      }

      @Override
      public String name() {
        return name;
      }

      @Override
      public int order() {
        return order;
      }

      @Override
      public Class<String> resultType() {
        return String.class;
      }

      @Override
      public String execute(ExecutionContext context) {
        throw new AssertionError("Skipped step must not execute");
      }
    };
  }
}

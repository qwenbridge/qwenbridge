package io.qwenbridge.pipeline;

import io.qwenbridge.event.model.PipelineEvents;
import io.qwenbridge.event.snapshot.PipelineContextSnapshotFactory;
import io.qwenbridge.event.spi.PipelineEventPublisher;
import io.qwenbridge.pipeline.result.PipelineTraceItem;
import io.qwenbridge.pipeline.step.PipelineStep;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PipelineEngine {

    private final List<PipelineStep<?>> steps;

    private final PipelineEventPublisher publisher;

    private final PipelineContextSnapshotFactory snapshotFactory;

    public void execute(ExecutionContext context) {

        publisher.publish(
                PipelineEvents.pipelineStarted(
                        snapshotFactory.create(context)
                )
        );

        try {

            for (PipelineStep<?> step : steps.stream()
                    .sorted(Comparator.comparingInt(PipelineStep::order))
                    .toList()) {

                if (context.stopped()) {

                    context.addTrace(
                            new PipelineTraceItem(
                                    step.name(),
                                    "SKIPPED",
                                    0
                            )
                    );

                    publisher.publish(
                            PipelineEvents.pipelineStopped(
                                    snapshotFactory.create(context)
                            )
                    );

                    continue;
                }

                executeStep(step, context);
            }

            publisher.publish(
                    PipelineEvents.pipelineCompleted(
                            snapshotFactory.create(context)
                    )
            );

        } catch (Exception ex) {

            publisher.publish(
                    PipelineEvents.pipelineFailed(
                            snapshotFactory.create(context)
                    )
            );

            throw ex;
        }
    }

    private <T> void executeStep(
            PipelineStep<T> step,
            ExecutionContext context
    ) {

        if (step.publishEvents()) {
            publisher.publish(
                    PipelineEvents.stepStarted(
                            step.stage(),
                            snapshotFactory.create(context)
                    )
            );
        }

        Instant startedAt = Instant.now();

        try {

            T result = step.execute(context);

            context.store(step.resultType(), result);

            long durationMs =
                    Duration.between(startedAt, Instant.now()).toMillis();

            context.addTrace(
                    new PipelineTraceItem(
                            step.name(),
                            "EXECUTED",
                            durationMs
                    )
            );

            if (step.publishEvents()) {
                publisher.publish(
                        PipelineEvents.stepCompleted(
                                step.stage(),
                                snapshotFactory.create(context)
                        )
                );
            }

        } catch (RuntimeException ex) {

            if (step.publishEvents()) {
                publisher.publish(
                        PipelineEvents.stepFailed(
                                step.stage(),
                                snapshotFactory.create(context)
                        )
                );
            }

            throw ex;
        }
    }

}

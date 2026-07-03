package io.qwenbridge.pipeline;

import io.qwenbridge.event.model.PipelineEventMetadata;
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
import java.util.concurrent.atomic.AtomicLong;

@Component
@RequiredArgsConstructor
public class PipelineEngine {

    private final List<PipelineStep<?>> steps;

    private final PipelineEventPublisher publisher;

    private final PipelineContextSnapshotFactory snapshotFactory;

    public void execute(ExecutionContext context) {

        AtomicLong sequence = new AtomicLong(0L);

        publish(
                PipelineEvents.pipelineStarted(
                        snapshotFactory.create(context),
                        metadata(context, sequence)
                )
        );

        try {

            boolean stoppedPublished = false;

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

                    if (!stoppedPublished) {
                        publish(
                                PipelineEvents.pipelineStopped(
                                        snapshotFactory.create(context),
                                        metadata(context, sequence)
                                )
                        );

                        stoppedPublished = true;
                    }

                    continue;
                }

                executeStep(step, context, sequence);
            }

            publish(
                    PipelineEvents.pipelineCompleted(
                            snapshotFactory.create(context),
                            metadata(context, sequence)
                    )
            );

        } catch (Exception ex) {

            publish(
                    PipelineEvents.pipelineFailed(
                            snapshotFactory.create(context),
                            metadata(context, sequence)
                    )
            );

            throw ex;
        }
    }

    private <T> void executeStep(
            PipelineStep<T> step,
            ExecutionContext context,
            AtomicLong sequence
    ) {

        if (step.publishEvents()) {
            publish(
                    PipelineEvents.stepStarted(
                            step.stage(),
                            snapshotFactory.create(context),
                            metadata(context, sequence)
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
                publish(
                        PipelineEvents.stepCompleted(
                                step.stage(),
                                snapshotFactory.create(context),
                                metadata(context, sequence)
                        )
                );
            }

        } catch (RuntimeException ex) {

            if (step.publishEvents()) {
                publish(
                        PipelineEvents.stepFailed(
                                step.stage(),
                                snapshotFactory.create(context),
                                metadata(context, sequence)
                        )
                );
            }

            throw ex;
        }
    }

    private PipelineEventMetadata metadata(
            ExecutionContext context,
            AtomicLong sequence
    ) {
        return PipelineEventMetadata.of(
                context.request().requestId(),
                sequence.incrementAndGet()
        );
    }

    private void publish(io.qwenbridge.event.model.PipelineEvent<?> event) {
        publisher.publish(event);
    }

}

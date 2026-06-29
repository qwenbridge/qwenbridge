package io.qwenbridge.pipeline;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.pipeline.result.PipelineTraceItem;
import io.qwenbridge.pipeline.step.PipelineStep;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class PipelineEngine {

    private final List<PipelineStep<?>> steps;

    public void execute(ExecutionContext context) {
        for (PipelineStep<?> step : steps) {
            if (context.stopped()) {
                context.addTrace(new PipelineTraceItem(
                        step.name(),
                        "SKIPPED",
                        0
                ));
                continue;
            }

            executeStep(step, context);
        }
    }

    private <T> void executeStep(PipelineStep<T> step, ExecutionContext context) {
        Instant startedAt = Instant.now();

        T result = step.execute(context);
        context.store(step.resultType(), result);

        long durationMs = Duration.between(startedAt, Instant.now()).toMillis();

        context.addTrace(new PipelineTraceItem(
                step.name(),
                "EXECUTED",
                durationMs
        ));
    }
}

package io.omnisearch.pipeline.step;

import io.omnisearch.confidence.ConfidenceService;
import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.ConfidenceResult;
import io.omnisearch.pipeline.result.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ConfidenceStepTest {

    @Test
    void shouldReturnHighConfidenceForMultipleRewrites() {
        ExecutionContext context = new ExecutionContext("میز");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk", "table"))
        );

        ConfidenceStep step = new ConfidenceStep(new ConfidenceService());

        ConfidenceResult result = step.execute(context);

        assertThat(result.value()).isEqualTo(0.94);
    }

    @Test
    void shouldReturnDefaultConfidenceForSingleRewrite() {
        ExecutionContext context = new ExecutionContext("desk");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk"))
        );

        ConfidenceStep step = new ConfidenceStep(new ConfidenceService());

        ConfidenceResult result = step.execute(context);

        assertThat(result.value()).isEqualTo(0.80);
    }
}

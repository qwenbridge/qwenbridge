package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;
import io.qwenbridge.pipeline.result.SemanticResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticStepTest {

    @Test
    void shouldValidateSemanticForMultipleRewrites() {
        ExecutionContext context = new ExecutionContext("میز");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk", "table", "office desk"))
        );

        SemanticStep step = new SemanticStep();

        SemanticResult result = step.execute(context);

        assertThat(result.validated()).isTrue();
        assertThat(result.score()).isEqualTo(0.96);
    }
}

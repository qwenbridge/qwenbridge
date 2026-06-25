package io.omnisearch.pipeline.step;

import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.PolicyResult;
import io.omnisearch.pipeline.result.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PolicyStepTest {

    @Test
    void shouldPassCleanRewrites() {
        ExecutionContext context = new ExecutionContext("desk");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk", "office desk"))
        );

        PolicyStep step = new PolicyStep();

        PolicyResult result = step.execute(context);

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test
    void shouldBlockAdultContent() {
        ExecutionContext context = new ExecutionContext("adult desk");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("adult desk"))
        );

        PolicyStep step = new PolicyStep();

        PolicyResult result = step.execute(context);

        assertThat(result.passed()).isFalse();
        assertThat(result.violations()).contains("ADULT_CONTENT");
    }
}

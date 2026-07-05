package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.PolicyResult;
import io.qwenbridge.pipeline.result.RewriteResult;
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

    @Test
    void shouldPassNormalLongShoppingQueryRewrite() {
        ExecutionContext context = new ExecutionContext("best gaming laptop under 1500 euro");
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("best gaming laptop under 1500 euro"))
        );

        PolicyStep step = new PolicyStep();

        PolicyResult result = step.execute(context);

        assertThat(result.passed()).isTrue();
        assertThat(result.violations()).isEmpty();
    }
}

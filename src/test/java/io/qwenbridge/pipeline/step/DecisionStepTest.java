package io.qwenbridge.pipeline.step;

import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionStepTest {

    @Test
    void shouldReturnRewriteDecisionWhenMultipleRewritesExist() {
        ExecutionContext context = new ExecutionContext("میز");
        context.store(ConfidenceResult.class, new ConfidenceResult(0.94));
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk", "table"))
        );

        DecisionStep step = new DecisionStep(new DecisionService());

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.REWRITE);
    }

    @Test
    void shouldReturnAllowDecisionWhenSingleRewriteExists() {
        ExecutionContext context = new ExecutionContext("desk");
        context.store(ConfidenceResult.class, new ConfidenceResult(0.80));
        context.store(
                RewriteResult.class,
                new RewriteResult(true, "mock", List.of("desk"))
        );

        DecisionStep step = new DecisionStep(new DecisionService());

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.ALLOW);
    }
}

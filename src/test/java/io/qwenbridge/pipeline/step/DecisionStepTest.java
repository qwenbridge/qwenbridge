package io.qwenbridge.pipeline.step;

import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionStepTest {

    @Test
    void shouldReturnRewriteDecisionWhenSearchDecisionRequiresRewrite() {
        ExecutionContext context = new ExecutionContext("میز");

        DecisionService decisionService = new DecisionService(ctx -> new SearchDecision(
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                true,
                false,
                true,
                false,
                0.75,
                "Rewrite is required before search execution."
        ));

        DecisionStep step = new DecisionStep(
                decisionService,
                new ExecutionPlanFactory()
        );

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.REWRITE);
    }

    @Test
    void shouldReturnAllowDecisionWhenSearchDecisionDoesNotRequireRewrite() {
        ExecutionContext context = new ExecutionContext("desk");

        DecisionService decisionService =
                new DecisionService(ctx -> SearchDecision.keyword());

        DecisionStep step = new DecisionStep(
                decisionService,
                new ExecutionPlanFactory()
        );

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.ALLOW);
    }
}

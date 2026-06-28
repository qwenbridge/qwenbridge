package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.execution.DefaultExecutionEngine;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.execution.executor.DirectAnswerExecutor;
import io.qwenbridge.execution.executor.KeywordSearchExecutor;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DecisionStepTest {

    @Test
    void shouldReturnRewriteDecisionWhenAnalysisRequiresRewrite() {
        ExecutionContext context = new ExecutionContext("میز");
        context.store(SearchAnalysis.class, analysis(true));

        DecisionStep step = step();

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.REWRITE);
    }

    @Test
    void shouldReturnAllowDecisionWhenAnalysisDoesNotRequireRewrite() {
        ExecutionContext context = new ExecutionContext("desk");
        context.store(SearchAnalysis.class, analysis(false));

        DecisionStep step = step();

        DecisionResult result = step.execute(context);

        assertThat(result.type()).isEqualTo(DecisionType.ALLOW);
    }

    private static DecisionStep step() {
        return new DecisionStep(
                new ExecutionPlanFactory(),
                new DefaultExecutionEngine(List.of(
                        new KeywordSearchExecutor(),
                        new DirectAnswerExecutor()
                ))
        );
    }

    private static SearchAnalysis analysis(boolean rewriteAgain) {
        return new SearchAnalysis(
                "en",
                IntentType.PRODUCT_SEARCH,
                0.85,
                "User searches for a product.",
                List.of("desk"),
                true,
                0.90,
                "User searches for a desk.",
                List.of("desk"),
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                true,
                false,
                rewriteAgain,
                false,
                0.80,
                rewriteAgain
                        ? "Rewrite is required before search execution."
                        : "Keyword search is enough."
        );
    }
}

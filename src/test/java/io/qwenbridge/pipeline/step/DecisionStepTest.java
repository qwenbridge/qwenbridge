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
        return SearchAnalysis.builder()
                .language("en")
                .intent(IntentType.PRODUCT_SEARCH)
                .intentConfidence(0.85)
                .intentReason("User searches for a product.")
                .rewrites(List.of("desk"))
                .semanticValidated(true)
                .semanticScore(0.90)
                .semanticMeaning("User searches for a desk.")
                .entities(List.of("desk"))
                .searchMode(SearchMode.KEYWORD)
                .backend(SearchBackend.IN_MEMORY)
                .keywordSearch(true)
                .vectorSearch(false)
                .hybridSearch(false)
                .facets(true)
                .rerank(false)
                .rewriteAgain(rewriteAgain)
                .answer(false)
                .decisionConfidence(0.80)
                .decisionReason(rewriteAgain
                        ? "Rewrite is required before search execution."
                        : "Keyword search is enough.")
                .build();
    }
}

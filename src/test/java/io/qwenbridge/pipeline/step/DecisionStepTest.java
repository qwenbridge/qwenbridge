package io.qwenbridge.pipeline.step;

import io.qwenbridge.ai.service.AIService;
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
import io.qwenbridge.execution.provider.implementation.InMemorySearchProvider;
import io.qwenbridge.execution.provider.registry.DefaultSearchProviderRegistry;
import io.qwenbridge.execution.provider.resolver.DefaultSearchProviderResolver;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                new DefaultExecutionEngine(
                        List.of(
                                new KeywordSearchExecutor(),
                                new DirectAnswerExecutor()
                        ),
                        new DefaultSearchProviderResolver(
                                new DefaultSearchProviderRegistry(
                                        List.of(new InMemorySearchProvider())
                                )
                        ),
                        mock(AIService.class),
                        new io.qwenbridge.ranking.service.SearchResultRanker(new io.qwenbridge.ranking.policy.DefaultRankingPolicy()),
                        (query, resultSet) -> resultSet
                )
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

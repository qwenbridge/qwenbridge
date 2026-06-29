package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RewriteStepTest {

    @Test
    void shouldMapRewriteFromSearchAnalysis() {
        ExecutionContext context = new ExecutionContext("tabel");
        context.store(SearchAnalysis.class, analysis(List.of("table")));

        RewriteResult result = new RewriteStep().execute(context);

        assertThat(result.performed()).isTrue();
        assertThat(result.provider()).isEqualTo("qwen-analysis");
        assertThat(result.rewrites()).containsExactly("table");
    }

    @Test
    void shouldReturnNoneWhenSearchAnalysisIsMissing() {
        ExecutionContext context = new ExecutionContext("table");

        RewriteResult result = new RewriteStep().execute(context);

        assertThat(result.performed()).isFalse();
        assertThat(result.rewrites()).isEmpty();
    }

    private static SearchAnalysis analysis(List<String> rewrites) {
        return SearchAnalysis.builder()
                .language("en")
                .intent(IntentType.PRODUCT_SEARCH)
                .intentConfidence(0.80)
                .intentReason("Product search.")
                .rewrites(rewrites)
                .semanticValidated(true)
                .semanticScore(0.90)
                .semanticMeaning("User searches for a table.")
                .entities(List.of("table"))
                .searchMode(SearchMode.KEYWORD)
                .backend(SearchBackend.IN_MEMORY)
                .keywordSearch(true)
                .vectorSearch(false)
                .hybridSearch(false)
                .facets(true)
                .rerank(false)
                .rewriteAgain(false)
                .answer(false)
                .decisionConfidence(0.80)
                .decisionReason("Keyword search is enough.")
                .build();
    }
}

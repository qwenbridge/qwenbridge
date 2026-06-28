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
        return new SearchAnalysis(
                "en",
                IntentType.PRODUCT_SEARCH,
                0.80,
                "Product search.",
                rewrites,
                true,
                0.90,
                "User searches for a table.",
                List.of("table"),
                SearchMode.KEYWORD,
                SearchBackend.IN_MEMORY,
                true,
                false,
                false,
                true,
                false,
                false,
                false,
                0.80,
                "Keyword search is enough."
        );
    }
}

package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.SemanticResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SemanticStepTest {

    @Test
    void shouldMapSemanticResultFromSearchAnalysis() {
        ExecutionContext context = new ExecutionContext("cheap iphone");
        context.store(SearchAnalysis.class, analysis());

        SemanticResult result = new SemanticStep().execute(context);

        assertThat(result.validated()).isTrue();
        assertThat(result.score()).isEqualTo(0.9);
        assertThat(result.analysis()).isNotNull();
        assertThat(result.analysis().originalQuery()).isEqualTo("cheap iphone");
        assertThat(result.analysis().entities()).hasSize(1);
    }

    private static SearchAnalysis analysis() {
        return new SearchAnalysis(
                "en",
                IntentType.PRODUCT_SEARCH,
                0.85,
                "User searches for a product.",
                List.of("cheap iphone"),
                true,
                0.90,
                "User wants an affordable iPhone.",
                List.of("iphone"),
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

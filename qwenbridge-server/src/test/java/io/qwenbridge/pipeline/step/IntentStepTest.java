package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchMode;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IntentStepTest {

    @Test
    void shouldMapIntentFromSearchAnalysis() {
        ExecutionContext context = new ExecutionContext("only red shoes");
        context.store(SearchAnalysis.class, analysis());

        IntentResult result = new IntentStep().execute(context);

        assertThat(result.intent()).isEqualTo("FILTER");
        assertThat(result.confidence()).isEqualTo(0.82);
        assertThat(result.reason()).isEqualTo("User is narrowing search results.");
        assertThat(result.analysis()).isNotNull();
        assertThat(result.analysis().type()).isEqualTo(IntentType.FILTER);
    }

    private static SearchAnalysis analysis() {
        return SearchAnalysis.builder()
                .language("en")
                .intent(IntentType.FILTER)
                .intentConfidence(0.82)
                .intentReason("User is narrowing search results.")
                .rewrites(List.of("red shoes"))
                .semanticValidated(true)
                .semanticScore(0.90)
                .semanticMeaning("User wants red shoes.")
                .entities(List.of("shoes", "red"))
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

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
        return new SearchAnalysis(
                "en",
                IntentType.FILTER,
                0.82,
                "User is narrowing search results.",
                List.of("red shoes"),
                true,
                0.90,
                "User wants red shoes.",
                List.of("shoes", "red"),
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

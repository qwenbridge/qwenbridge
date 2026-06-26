package io.qwenbridge.pipeline.step;

import io.qwenbridge.intent.IntentAnalysis;
import io.qwenbridge.intent.IntentService;
import io.qwenbridge.intent.IntentType;
import io.qwenbridge.intent.ai.AIIntentService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentStepTest {

    @Test
    void shouldDetectIntentUsingIntentService() {
        AIIntentService aiIntentService = query -> new IntentAnalysis(
                IntentType.FILTER,
                "User is narrowing search results.",
                0.82
        );

        IntentService intentService = new IntentService(aiIntentService);
        IntentStep step = new IntentStep(intentService);

        IntentResult result = step.execute(new ExecutionContext("only red shoes"));

        assertThat(result.intent()).isEqualTo("FILTER");
        assertThat(result.confidence()).isEqualTo(0.82);
        assertThat(result.reason()).isEqualTo("User is narrowing search results.");
        assertThat(result.analysis()).isNotNull();
        assertThat(result.analysis().type()).isEqualTo(IntentType.FILTER);
    }
}

package io.qwenbridge.intent;

import io.qwenbridge.intent.ai.AIIntentService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentServiceTest {

    @Test
    void shouldReturnAIIntentWhenAISucceeds() {
        AIIntentService aiIntentService = query -> new IntentAnalysis(
                IntentType.FILTER,
                "User is filtering existing results.",
                0.8
        );

        IntentService service = new IntentService(aiIntentService);

        IntentAnalysis analysis = service.analyze("only red shoes");

        assertThat(analysis.type()).isEqualTo(IntentType.FILTER);
        assertThat(analysis.confidence()).isEqualTo(0.8);
        assertThat(service.detect("only red shoes")).isEqualTo("FILTER");
    }

    @Test
    void shouldFallbackToProductSearchWhenAIFails() {
        AIIntentService aiIntentService = query -> {
            throw new RuntimeException("ollama unavailable");
        };

        IntentService service = new IntentService(aiIntentService);

        IntentAnalysis analysis = service.analyze("iphone");

        assertThat(analysis.type()).isEqualTo(IntentType.PRODUCT_SEARCH);
        assertThat(analysis.confidence()).isEqualTo(0.5);
        assertThat(service.detect("iphone")).isEqualTo("PRODUCT_SEARCH");
    }
}

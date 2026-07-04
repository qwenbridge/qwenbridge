package io.qwenbridge.ai.provider.ollama;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class OllamaProviderIntegrationTest {

    @Autowired
    private AIService aiService;

    @Test
    void shouldChatWithRealOllama() {
        ChatResponse response = aiService.chat(
                new ChatRequest("Reply with exactly one word: QwenBridge")
        );

        assertThat(response).isNotNull();
        assertThat(response.content()).isNotBlank();
    }
}

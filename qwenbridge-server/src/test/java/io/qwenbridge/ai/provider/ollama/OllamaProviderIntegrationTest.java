package io.qwenbridge.ai.provider.ollama;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@QuarkusTest
@EnabledIfEnvironmentVariable(named = "QWENBRIDGE_RUN_OLLAMA_IT", matches = "true")
class OllamaProviderIntegrationTest {

  @Inject AIService aiService;

  @Test
  void shouldChatWithRealOllama() {
    ChatResponse response =
        aiService.chat(new ChatRequest("Reply with exactly one word: QwenBridge"));

    assertThat(response).isNotNull();
    assertThat(response.content()).isNotBlank();
  }
}

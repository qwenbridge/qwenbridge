package io.qwenbridge.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

@QuarkusTest
class AIServiceTest {

  @Inject AIService aiService;

  @Inject AIProviderResolver providerResolver;

  @Test
  void shouldLoadAIService() {
    assertThat(aiService).isNotNull();
  }

  @Test
  void shouldResolveDefaultProviderFromConfiguration() {
    AIProvider provider = providerResolver.resolveDefault();

    assertThat(provider).isNotNull();
    assertThat(provider.providerId().value()).isEqualTo("ollama");
  }
}

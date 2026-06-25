package io.qwenbridge.ai.service;

import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class AIServiceTest {

    @Autowired
    private AIService aiService;

    @Autowired
    private AIProviderResolver providerResolver;

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

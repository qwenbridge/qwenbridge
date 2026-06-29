package io.qwenbridge.ai.provider.resolver;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.ai.config.AIProperties;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderRegistry;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import io.qwenbridge.ai.value.ProviderId;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DefaultAIProviderResolver implements AIProviderResolver {

    private final AIProviderRegistry registry;
    private final AIProperties properties;

    @Override
    public AIProvider resolve(ProviderId providerId) {
        try {
            return registry.get(providerId);
        } catch (RuntimeException ex) {
            throw new AIException(
                    "AI provider not found: " + providerId.value(),
                    ex
            );
        }
    }

    @Override
    public AIProvider resolveDefault() {
        return resolve(new ProviderId(properties.provider()));
    }
}

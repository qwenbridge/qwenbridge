package io.qwenbridge.ai.provider.registry;

import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderRegistry;
import io.qwenbridge.ai.value.ProviderId;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class DefaultAIProviderRegistry implements AIProviderRegistry {

    private final Map<ProviderId, AIProvider> providers = new HashMap<>();

    public DefaultAIProviderRegistry(List<AIProvider> providers) {
        for (AIProvider provider : providers) {
            this.providers.put(provider.providerId(), provider);
        }
    }

    @Override
    public AIProvider get(ProviderId providerId) {
        AIProvider provider = providers.get(providerId);

        if (provider == null) {
            throw new IllegalArgumentException(
                    "Unknown AI provider: " + providerId
            );
        }

        return provider;
    }
}

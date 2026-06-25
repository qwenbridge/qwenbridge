package io.omnisearch.ai.provider.registry;

import io.omnisearch.ai.exception.AIException;
import io.omnisearch.ai.provider.spi.AIProvider;
import io.omnisearch.ai.provider.spi.AIProviderRegistry;
import io.omnisearch.ai.value.ProviderId;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DefaultAIProviderRegistry implements AIProviderRegistry {

    private final Map<ProviderId, AIProvider> providers;

    public DefaultAIProviderRegistry(List<AIProvider> discoveredProviders) {

        Map<ProviderId, AIProvider> registry = new LinkedHashMap<>();

        for (AIProvider provider : discoveredProviders) {

            ProviderId id = provider.id();

            if (registry.containsKey(id)) {
                throw new AIException(
                        "Duplicate AI provider registered: " + id
                );
            }

            registry.put(id, provider);
        }

        this.providers = Map.copyOf(registry);
    }

    @Override
    public Optional<AIProvider> find(ProviderId providerId) {
        return Optional.ofNullable(providers.get(providerId));
    }
}

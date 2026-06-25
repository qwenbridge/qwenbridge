package io.omnisearch.ai.provider.spi;

import io.omnisearch.ai.value.ProviderId;

import java.util.Optional;

public interface AIProviderRegistry {

    Optional<AIProvider> find(ProviderId providerId);
}

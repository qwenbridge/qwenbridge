package io.omnisearch.ai.provider.spi;

import io.omnisearch.ai.value.ProviderId;

public interface AIProviderRegistry {

    AIProvider get(ProviderId providerId);
}

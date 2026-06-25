package io.omnisearch.ai.provider.support;

import io.omnisearch.ai.provider.spi.AIProvider;
import io.omnisearch.ai.value.ProviderId;

public abstract class AbstractAIProvider implements AIProvider {

    private final ProviderId providerId;

    protected AbstractAIProvider(ProviderId providerId) {
        this.providerId = providerId;
    }

    @Override
    public ProviderId providerId() {
        return providerId;
    }
}

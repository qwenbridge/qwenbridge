package io.qwenbridge.ai.provider.support;

import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.value.ProviderId;

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

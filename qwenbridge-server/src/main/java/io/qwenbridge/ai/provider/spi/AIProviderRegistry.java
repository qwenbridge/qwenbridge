package io.qwenbridge.ai.provider.spi;

import io.qwenbridge.ai.value.ProviderId;

public interface AIProviderRegistry {

    AIProvider get(ProviderId providerId);
}

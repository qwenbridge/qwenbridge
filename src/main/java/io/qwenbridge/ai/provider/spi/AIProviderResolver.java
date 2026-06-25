package io.qwenbridge.ai.provider.spi;

import io.qwenbridge.ai.value.ProviderId;

public interface AIProviderResolver {

    AIProvider resolve(ProviderId providerId);

    AIProvider resolveDefault();
}

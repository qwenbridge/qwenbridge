package io.omnisearch.ai.provider.spi;

import io.omnisearch.ai.value.ProviderId;

public interface AIProviderResolver {

    AIProvider resolve(ProviderId providerId);
}

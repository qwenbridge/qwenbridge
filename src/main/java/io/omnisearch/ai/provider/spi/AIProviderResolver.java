package io.omnisearch.ai.provider.spi.spi;

public interface AIProviderResolver {

    AIProvider resolve(AIProviderType providerType);
}

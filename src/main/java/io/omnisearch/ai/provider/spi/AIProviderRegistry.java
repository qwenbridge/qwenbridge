package io.omnisearch.ai.provider.spi.spi;

public interface AIProviderRegistry {

    AIProvider get(AIProviderType type);
}

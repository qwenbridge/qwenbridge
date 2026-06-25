package io.qwenbridge.ai.service;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import org.springframework.stereotype.Service;

@Service
public class AIService {

    private final AIProviderResolver providerResolver;

    public AIService(AIProviderResolver providerResolver) {
        this.providerResolver = providerResolver;
    }

    public ChatResponse chat(ChatRequest request) {
        return providerResolver.resolveDefault().chat(request);
    }

    public EmbeddingResponse embed(EmbeddingRequest request) {
        return providerResolver.resolveDefault().embed(request);
    }
}

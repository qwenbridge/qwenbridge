package io.qwenbridge.ai.provider.spi;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.value.ProviderId;

public interface AIProvider {

    ProviderId providerId();

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}

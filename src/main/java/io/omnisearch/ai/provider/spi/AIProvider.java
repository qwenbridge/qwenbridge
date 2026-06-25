package io.omnisearch.ai.provider.spi;

import io.omnisearch.ai.contract.ChatRequest;
import io.omnisearch.ai.contract.ChatResponse;
import io.omnisearch.ai.contract.EmbeddingRequest;
import io.omnisearch.ai.contract.EmbeddingResponse;
import io.omnisearch.ai.value.ProviderId;

public interface AIProvider {

    ProviderId providerId();

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}

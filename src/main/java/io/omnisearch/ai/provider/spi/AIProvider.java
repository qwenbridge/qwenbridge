package io.omnisearch.ai.provider.spi.spi;

import io.omnisearch.ai.contract.ChatRequest;
import io.omnisearch.ai.contract.ChatResponse;
import io.omnisearch.ai.contract.EmbeddingRequest;
import io.omnisearch.ai.contract.EmbeddingResponse;

public interface AIProvider {

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}

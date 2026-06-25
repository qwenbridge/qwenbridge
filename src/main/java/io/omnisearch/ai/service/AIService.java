package io.omnisearch.ai.service;

import io.omnisearch.ai.contract.ChatRequest;
import io.omnisearch.ai.contract.ChatResponse;
import io.omnisearch.ai.contract.EmbeddingRequest;
import io.omnisearch.ai.contract.EmbeddingResponse;

public interface AIService {

    ChatResponse chat(ChatRequest request);

    EmbeddingResponse embed(EmbeddingRequest request);
}

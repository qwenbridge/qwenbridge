package io.qwenbridge.ai.provider.spi;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.value.ProviderId;
import io.smallrye.mutiny.Multi;

public interface AIProvider {

  ProviderId providerId();

  ChatResponse chat(ChatRequest request);

  Multi<StreamingChatChunk> streamChat(StreamingChatRequest request);

  EmbeddingResponse embed(EmbeddingRequest request);
}

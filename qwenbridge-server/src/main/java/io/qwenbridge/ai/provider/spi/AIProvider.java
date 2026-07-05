package io.qwenbridge.ai.provider.spi;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.value.ProviderId;
import reactor.core.publisher.Flux;

public interface AIProvider {

  ProviderId providerId();

  ChatResponse chat(ChatRequest request);

  Flux<StreamingChatChunk> streamChat(StreamingChatRequest request);

  EmbeddingResponse embed(EmbeddingRequest request);
}

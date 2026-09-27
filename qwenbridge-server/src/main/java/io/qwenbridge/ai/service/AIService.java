package io.qwenbridge.ai.service;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import lombok.RequiredArgsConstructor;
import jakarta.enterprise.context.ApplicationScoped;
import io.smallrye.mutiny.Multi;

@ApplicationScoped
@RequiredArgsConstructor
public class AIService {

  private final AIProviderResolver providerResolver;

  public ChatResponse chat(ChatRequest request) {
    return providerResolver.resolveDefault().chat(request);
  }

  public Multi<StreamingChatChunk> streamChat(StreamingChatRequest request) {
    return providerResolver.resolveDefault().streamChat(request);
  }

  public EmbeddingResponse embed(EmbeddingRequest request) {
    return providerResolver.resolveDefault().embed(request);
  }
}

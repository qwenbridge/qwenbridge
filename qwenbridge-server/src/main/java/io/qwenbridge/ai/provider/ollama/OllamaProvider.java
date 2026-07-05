package io.qwenbridge.ai.provider.ollama;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.provider.ollama.client.OllamaClient;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaChatResponse;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingRequest;
import io.qwenbridge.ai.provider.ollama.dto.OllamaEmbeddingResponse;
import io.qwenbridge.ai.provider.support.AbstractAIProvider;
import io.qwenbridge.ai.value.ProviderId;
import java.util.List;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
public class OllamaProvider extends AbstractAIProvider {

  private static final ProviderId PROVIDER_ID = new ProviderId("ollama");

  private final OllamaClient client;
  private final OllamaProperties properties;

  public OllamaProvider(OllamaClient client, OllamaProperties properties) {
    super(PROVIDER_ID);
    this.client = client;
    this.properties = properties;
  }

  @Override
  public ChatResponse chat(ChatRequest request) {
    OllamaChatRequest ollamaRequest =
        new OllamaChatRequest(
            properties.chatModel(),
            List.of(new OllamaChatRequest.Message("user", request.prompt())),
            false);

    OllamaChatResponse response = client.chat(ollamaRequest);

    return new ChatResponse(response.message() == null ? "" : response.message().content());
  }

  @Override
  public Flux<StreamingChatChunk> streamChat(StreamingChatRequest request) {
    OllamaChatRequest ollamaRequest =
        new OllamaChatRequest(
            properties.chatModel(),
            List.of(new OllamaChatRequest.Message("user", request.prompt())),
            true);

    return client
        .streamChat(ollamaRequest)
        .map(response -> new StreamingChatChunk(response.content(), response.done()))
        .takeUntil(StreamingChatChunk::done);
  }

  @Override
  public EmbeddingResponse embed(EmbeddingRequest request) {
    OllamaEmbeddingRequest ollamaRequest =
        new OllamaEmbeddingRequest(properties.embeddingModel(), request.text());

    OllamaEmbeddingResponse response = client.embed(ollamaRequest);

    return new EmbeddingResponse(response.firstEmbedding());
  }
}

package io.qwenbridge.ai.service;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.contract.EmbeddingRequest;
import io.qwenbridge.ai.contract.EmbeddingResponse;
import io.qwenbridge.ai.contract.StreamingChatChunk;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.exception.AIException;
import io.qwenbridge.ai.provider.spi.AIProviderResolver;
import io.qwenbridge.ai.routing.ChatRouting;
import io.qwenbridge.core.ai.ChatResult;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Chat, streaming and embedding through the configured provider. With {@code
 * qwenbridge.ai.fallback-providers} set, chat goes through {@link ChatRouting}: a fallback answers
 * only after a provider fault, and that is logged and reported (QB-248). Streaming and embedding
 * never fall back: a stream is not stitched from two models, and embeddings from another model
 * would not be comparable with the index (QB-247).
 */
@ApplicationScoped
public class AIService {

  private static final Logger LOG = Logger.getLogger(AIService.class);

  /** Which provider answered a chat call, and whether it was a fallback. */
  public record Served(ChatResponse response, String provider, boolean fallback) {}

  private final AIProviderResolver providerResolver;
  private final ChatRouting routing;

  @Inject
  public AIService(AIProviderResolver providerResolver, ChatRouting routing) {
    this.providerResolver = providerResolver;
    this.routing = routing;
  }

  public AIService(AIProviderResolver providerResolver) {
    this(providerResolver, null);
  }

  private static final ThreadLocal<Served> LAST = new ThreadLocal<>();

  public ChatResponse chat(ChatRequest request) {
    Served served = chatServed(request);
    LAST.set(served);
    return served.response();
  }

  /**
   * Who answered the last {@link #chat} on this thread, then forgets it; null when unknown. Lets
   * {@code /api/v1/ai/chat} report the provider without changing the {@code chat} contract.
   */
  public Served takeServed() {
    Served served = LAST.get();
    LAST.remove();
    return served;
  }

  public Served chatServed(ChatRequest request) {
    if (routing == null || !routing.active()) {
      var provider = providerResolver.resolveDefault();
      return new Served(provider.chat(request), provider.providerId().value(), false);
    }
    var routed = routing.route(request);
    if (routed.fallbackUsed())
      LOG.warnf(
          "AI chat answered by fallback provider %s; attempts %s",
          routed.servedBy(), routed.attempts());
    return switch (routed.result()) {
      case ChatResult.Completed c ->
          new Served(new ChatResponse(c.text()), routed.servedBy(), routed.fallbackUsed());
      case ChatResult.Failed f ->
          throw new AIException("AI providers failed: " + routed.attempts());
    };
  }

  public Multi<StreamingChatChunk> streamChat(StreamingChatRequest request) {
    return providerResolver.resolveDefault().streamChat(request);
  }

  public EmbeddingResponse embed(EmbeddingRequest request) {
    return providerResolver.resolveDefault().embed(request);
  }
}

package io.qwenbridge.ai.routing;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.StreamingChatRequest;
import io.qwenbridge.ai.provider.http.JsonHttpProvider;
import io.qwenbridge.ai.provider.ollama.config.OllamaProperties;
import io.qwenbridge.ai.provider.spi.AIProvider;
import io.qwenbridge.ai.provider.spi.AIProviderRegistry;
import io.qwenbridge.ai.value.ProviderId;
import io.qwenbridge.core.ai.ChatCall;
import io.qwenbridge.core.ai.ChatCapabilities;
import io.qwenbridge.core.ai.ChatProvider;
import io.qwenbridge.core.ai.ChatResult;
import io.qwenbridge.core.ai.ChatRouter;
import io.qwenbridge.core.ai.ModelDescriptor;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * The server's chat providers behind the Core chat SPI (QB-243, QB-246 – QB-248).
 *
 * <ul>
 *   <li>{@code qwenbridge.ai.provider} selects the primary provider ({@code ollama} or {@code
 *       http-json}); switching it changes no public API or QueryIR contract.
 *   <li>{@code qwenbridge.ai.fallback-providers} lists fallbacks in order (default none). Only then
 *       does routing apply: a fallback answers only after a provider fault, and the response names
 *       the provider that answered.
 *   <li>{@code qwenbridge.ai.chat.model-version} and {@code qwenbridge.ai.chat.max-input-chars}
 *       describe the primary chat model; credentials are never part of model metadata.
 * </ul>
 */
@Singleton
public final class ChatRouting {

  private final ChatRouter router;
  private final List<String> fallbacks;
  private final Duration timeout;

  public ChatRouting(
      AIProviderRegistry registry,
      OllamaProperties ollama,
      JsonHttpProvider httpJson,
      @ConfigProperty(name = "qwenbridge.ai.provider", defaultValue = "ollama") String primary,
      @ConfigProperty(name = "qwenbridge.ai.fallback-providers") Optional<List<String>> fallbacks,
      @ConfigProperty(name = "qwenbridge.ai.chat.model-version", defaultValue = "unversioned")
          String modelVersion,
      @ConfigProperty(name = "qwenbridge.ai.chat.max-input-chars", defaultValue = "32000")
          int maxInputChars,
      @ConfigProperty(name = "qwenbridge.ai.chat.timeout", defaultValue = "60s") Duration timeout) {
    this.fallbacks = List.copyOf(fallbacks.orElse(List.of()));
    this.timeout = timeout;
    List<ChatProvider> providers = new ArrayList<>();
    List<String> ids = new ArrayList<>();
    ids.add(primary);
    ids.addAll(this.fallbacks);
    for (String id : ids) {
      AIProvider provider;
      try {
        provider = registry.get(new ProviderId(id));
      } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Unknown AI provider in configuration: " + id);
      }
      if (provider == httpJson && !httpJson.configured() && !id.equals(primary))
        throw new IllegalArgumentException(
            "Fallback provider http-json is not configured (qwenbridge.ai.http-json.base-url)");
      providers.add(adapt(provider, ollama, httpJson, modelVersion, maxInputChars));
    }
    router =
        new ChatRouter(
            providers,
            this.fallbacks.isEmpty()
                ? ChatRouter.FallbackPolicy.NONE
                : ChatRouter.FallbackPolicy.ON_PROVIDER_FAULT);
  }

  static ChatProvider adapt(
      AIProvider provider,
      OllamaProperties ollama,
      JsonHttpProvider httpJson,
      String modelVersion,
      int maxInputChars) {
    String id = provider.providerId().value();
    ModelDescriptor model =
        provider == httpJson
            ? new ModelDescriptor(id, "gateway", httpJson.manifest().version(), Map.of())
            : new ModelDescriptor(
                id,
                id.equals("ollama") ? ollama.chatModel() : id,
                modelVersion,
                Map.of("structured-output-temperature", "0"));
    ChatCapabilities capabilities =
        new ChatCapabilities(
            !id.equals("ollama") || ollama.streamingEnabled(), true, true, maxInputChars, 8192);
    return new ChatProvider() {
      @Override
      public String id() {
        return id;
      }

      @Override
      public ModelDescriptor model() {
        return model;
      }

      @Override
      public ChatCapabilities capabilities() {
        return capabilities;
      }

      @Override
      public ChatResult complete(ChatCall call, Consumer<String> tokens) {
        Map<String, Object> schema = call.outputSchema().orElse(null);
        if (!call.streaming())
          return new ChatResult.Completed(
              provider.chat(new ChatRequest(call.prompt(), schema)).content(), model);
        var text = new StringBuilder();
        provider
            .streamChat(new StreamingChatRequest(call.prompt(), schema))
            .subscribe()
            .asIterable()
            .forEach(
                chunk -> {
                  if (call.cancelled().getAsBoolean())
                    throw new java.util.concurrent.CancellationException();
                  if (chunk.content() != null && !chunk.content().isEmpty()) {
                    text.append(chunk.content());
                    tokens.accept(chunk.content());
                  }
                });
        return new ChatResult.Completed(text.toString(), model);
      }
    };
  }

  /** Whether fallbacks are configured; without them calls go straight to the primary. */
  public boolean active() {
    return !fallbacks.isEmpty();
  }

  public ChatRouter router() {
    return router;
  }

  public List<String> fallbacks() {
    return fallbacks;
  }

  public ChatRouter.Routed route(ChatRequest request) {
    return router.route(
        new ChatCall(
            request.prompt(),
            Optional.ofNullable(request.outputSchema()),
            false,
            1024,
            timeout,
            () -> false),
        t -> {});
  }
}

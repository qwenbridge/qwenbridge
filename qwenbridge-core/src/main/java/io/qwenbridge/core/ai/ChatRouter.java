package io.qwenbridge.core.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Explicit chat provider selection and fallback (QB-243, QB-248). The configured primary provider
 * is tried first; fallbacks are tried only when the policy allows it and only after a provider
 * fault (unavailable, timeout, rate limit, missing capability), never after a cancellation or a bad
 * request. A streamed answer is never stitched together from two providers: once text has been
 * streamed, a failure is returned as is.
 *
 * <p>The result always names the provider that answered and whether a fallback was used, so a
 * degraded answer is never presented as the primary model's. The router runs below authorization:
 * callers are authorized before they reach it, and switching providers changes nothing about that.
 */
public final class ChatRouter {

  public enum FallbackPolicy {
    /** Only the primary provider is used. */
    NONE,
    /** Fallbacks in order after a provider fault. */
    ON_PROVIDER_FAULT
  }

  public record Attempt(String provider, String outcome) {}

  public record Routed(
      ChatResult result, String servedBy, boolean fallbackUsed, List<Attempt> attempts) {
    public Routed {
      Objects.requireNonNull(result, "result must not be null");
      attempts = List.copyOf(attempts);
    }
  }

  private final List<ChatProvider> providers;
  private final FallbackPolicy policy;

  /**
   * @param providers the primary first, then fallbacks in order
   */
  public ChatRouter(List<ChatProvider> providers, FallbackPolicy policy) {
    this.providers = List.copyOf(providers);
    this.policy = Objects.requireNonNull(policy, "policy must not be null");
    if (this.providers.stream().map(ChatProvider::id).distinct().count() != this.providers.size())
      throw new IllegalArgumentException("duplicate chat provider");
  }

  public List<ChatProvider> providers() {
    return providers;
  }

  public Routed route(ChatCall call, Consumer<String> tokens) {
    if (providers.isEmpty())
      return new Routed(
          new ChatResult.Failed(ChatResult.Failure.NO_PROVIDER, "none"), "none", false, List.of());
    List<Attempt> attempts = new ArrayList<>();
    ChatResult last = null;
    String lastProvider = providers.getFirst().id();
    int limit = policy == FallbackPolicy.NONE ? 1 : providers.size();
    for (int i = 0; i < limit; i++) {
      ChatProvider provider = providers.get(i);
      lastProvider = provider.id();
      if (call.cancelled().getAsBoolean()) {
        last = new ChatResult.Failed(ChatResult.Failure.CANCELLED, provider.id());
        attempts.add(new Attempt(provider.id(), "CANCELLED"));
        break;
      }
      var unmet = AiCapabilities.unmet(provider.capabilities(), call);
      if (unmet.isPresent()) {
        last = new ChatResult.Failed(ChatResult.Failure.UNSUPPORTED, provider.id());
        attempts.add(new Attempt(provider.id(), "UNSUPPORTED: " + unmet.get()));
        continue;
      }
      var streamed = new AtomicBoolean();
      last =
          call(
              provider,
              call,
              text -> {
                streamed.set(true);
                tokens.accept(text);
              });
      attempts.add(
          new Attempt(
              provider.id(),
              last instanceof ChatResult.Failed f ? f.failure().name() : "COMPLETED"));
      if (last instanceof ChatResult.Completed
          || !(last instanceof ChatResult.Failed f && f.failure().providerFault())
          || streamed.get()) {
        return new Routed(last, provider.id(), i > 0, attempts);
      }
    }
    return new Routed(last, lastProvider, attempts.size() > 1, attempts);
  }

  /** One provider call with the call's timeout and cancellation; exceptions become UNAVAILABLE. */
  static ChatResult call(ChatProvider provider, ChatCall call, Consumer<String> tokens) {
    var future = new CompletableFuture<ChatResult>();
    Thread worker =
        Thread.ofVirtual()
            .name("qwenbridge-chat-" + provider.id())
            .start(
                () -> {
                  try {
                    future.complete(provider.complete(call, tokens));
                  } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                  }
                });
    long deadline = System.nanoTime() + call.timeout().toNanos();
    try {
      while (true) {
        if (call.cancelled().getAsBoolean()) {
          worker.interrupt();
          return new ChatResult.Failed(ChatResult.Failure.CANCELLED, provider.id());
        }
        long left = deadline - System.nanoTime();
        if (left <= 0) {
          worker.interrupt();
          return new ChatResult.Failed(ChatResult.Failure.TIMEOUT, provider.id());
        }
        try {
          ChatResult result = future.get(Math.min(left, 50_000_000L), TimeUnit.NANOSECONDS);
          if (result == null
              || result instanceof ChatResult.Completed c && !c.model().equals(provider.model()))
            return new ChatResult.Failed(ChatResult.Failure.INVALID_OUTPUT, provider.id());
          return result;
        } catch (TimeoutException poll) {
          // keep waiting until the deadline or a cancellation
        }
      }
    } catch (ExecutionException e) {
      return new ChatResult.Failed(ChatResult.Failure.UNAVAILABLE, provider.id());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      worker.interrupt();
      return new ChatResult.Failed(ChatResult.Failure.CANCELLED, provider.id());
    }
  }
}

package io.qwenbridge.core.embedding;

import io.qwenbridge.core.embedding.EmbeddingResult.Embedded;
import io.qwenbridge.core.embedding.EmbeddingResult.Failed;
import io.qwenbridge.core.embedding.EmbeddingResult.Failure;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Calls an optional {@link EmbeddingProvider} with the rules the provider must not be trusted to
 * apply itself (QB-110, QB-111): limits are checked first, the timeout is enforced, exceptions
 * become {@code UNAVAILABLE}, and the output must have one finite vector per input in the declared
 * dimensions. Without a provider every call answers {@code NO_PROVIDER}.
 */
public final class EmbeddingStage {

  private final Optional<EmbeddingProvider> provider;
  private final Duration timeout;

  public EmbeddingStage(Optional<EmbeddingProvider> provider, Duration timeout) {
    this.provider = provider == null ? Optional.empty() : provider;
    this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
  }

  /** The provider's model, when one is configured (QB-246). */
  public Optional<EmbeddingModel> model() {
    return provider.map(EmbeddingProvider::model);
  }

  public Optional<String> providerId() {
    return provider.map(EmbeddingProvider::id);
  }

  public Optional<EmbeddingSpace> space() {
    return provider.map(p -> p.model().space());
  }

  public EmbeddingResult embed(List<String> texts) {
    if (provider.isEmpty()) {
      return new Failed(Failure.NO_PROVIDER, "none");
    }
    EmbeddingProvider p = provider.get();
    EmbeddingModel model = p.model();
    if (texts.isEmpty() || texts.size() > model.maxBatch()) {
      return new Failed(Failure.BATCH_TOO_LARGE, p.id());
    }
    if (texts.stream().anyMatch(t -> t == null || t.length() > model.maxInputChars())) {
      return new Failed(Failure.INPUT_TOO_LARGE, p.id());
    }
    List<String> input = List.copyOf(texts);
    CompletableFuture<EmbeddingResult> future = new CompletableFuture<>();
    Thread worker =
        Thread.ofVirtual()
            .name("qwenbridge-embedding")
            .start(
                () -> {
                  try {
                    future.complete(p.embed(input, timeout));
                  } catch (RuntimeException e) {
                    future.completeExceptionally(e);
                  }
                });
    EmbeddingResult result;
    try {
      result = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    } catch (TimeoutException e) {
      worker.interrupt();
      return new Failed(Failure.TIMEOUT, p.id());
    } catch (ExecutionException e) {
      return new Failed(Failure.UNAVAILABLE, p.id());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      worker.interrupt();
      return new Failed(Failure.TIMEOUT, p.id());
    }
    if (result instanceof Embedded embedded && !valid(embedded, input.size(), model.space())) {
      return new Failed(Failure.INVALID_OUTPUT, p.id());
    }
    return result == null ? new Failed(Failure.INVALID_OUTPUT, p.id()) : result;
  }

  private static boolean valid(Embedded e, int count, EmbeddingSpace space) {
    if (!e.space().equals(space) || e.vectors().size() != count) {
      return false;
    }
    for (double[] v : e.vectors()) {
      if (v.length != space.dimensions()) {
        return false;
      }
      for (double x : v) {
        if (!Double.isFinite(x)) {
          return false;
        }
      }
    }
    return true;
  }
}

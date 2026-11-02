package io.qwenbridge.core.ai;

import io.qwenbridge.core.embedding.EmbeddingModel;
import io.qwenbridge.core.embedding.EmbeddingProvider;
import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.IndexCompatibility;
import java.time.Duration;
import java.util.List;

/**
 * Embedding fallback that can never mix vector spaces (QB-247, QB-248). Every provider must embed
 * into exactly the primary's {@link io.qwenbridge.core.embedding.EmbeddingSpace} (model, version,
 * dimensions); otherwise construction fails with the reindex reasons. At run time a fallback is
 * tried only after the previous provider was unavailable or timed out.
 */
public final class FallbackEmbeddingProvider implements EmbeddingProvider {

  private final List<EmbeddingProvider> providers;

  public FallbackEmbeddingProvider(List<EmbeddingProvider> providers) {
    this.providers = List.copyOf(providers);
    if (this.providers.isEmpty()) throw new IllegalArgumentException("no embedding provider");
    var primary = this.providers.getFirst().model().space();
    for (var other : this.providers.subList(1, this.providers.size())) {
      var compatibility = IndexCompatibility.check(primary, other.model().space());
      if (!compatibility.compatible())
        throw new IllegalArgumentException(
            "embedding fallback "
                + other.id()
                + " is not in the primary space "
                + primary
                + ": "
                + compatibility.reindexReasons());
    }
  }

  @Override
  public String id() {
    return providers.getFirst().id();
  }

  /** The primary's model, with the smallest limits of all providers. */
  @Override
  public EmbeddingModel model() {
    var primary = providers.getFirst().model();
    int batch = providers.stream().mapToInt(p -> p.model().maxBatch()).min().orElseThrow();
    int chars = providers.stream().mapToInt(p -> p.model().maxInputChars()).min().orElseThrow();
    return new EmbeddingModel(primary.space(), batch, chars);
  }

  @Override
  public EmbeddingResult embed(List<String> texts, Duration timeout) {
    EmbeddingResult last = null;
    for (var provider : providers) {
      try {
        last = provider.embed(texts, timeout);
      } catch (RuntimeException e) {
        last = new EmbeddingResult.Failed(EmbeddingResult.Failure.UNAVAILABLE, provider.id());
      }
      if (!(last instanceof EmbeddingResult.Failed f)
          || (f.failure() != EmbeddingResult.Failure.UNAVAILABLE
              && f.failure() != EmbeddingResult.Failure.TIMEOUT)) return last;
    }
    return last;
  }
}

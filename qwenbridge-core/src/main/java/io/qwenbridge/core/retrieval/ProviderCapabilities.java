package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * What a provider can do (QB-103, QB-107).
 *
 * @param vectorSpace the embedding space of the provider's vectors, when it supports VECTOR
 */
public record ProviderCapabilities(
    Set<RetrievalMode> modes,
    Set<FilterFeature> filters,
    Optional<EmbeddingSpace> vectorSpace,
    int maxTopK,
    boolean cancellable) {

  public ProviderCapabilities {
    modes = modes.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(modes));
    filters = filters.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(filters));
    vectorSpace = vectorSpace == null ? Optional.empty() : vectorSpace;
    if (maxTopK < 1) {
      throw new IllegalArgumentException("maxTopK must be positive");
    }
    if (modes.contains(RetrievalMode.VECTOR) && vectorSpace.isEmpty()) {
      throw new IllegalArgumentException("a vector provider must declare its embedding space");
    }
  }

  /** The first requirement of the request this provider cannot meet, if any. */
  public Optional<String> unmet(SearchRequest request) {
    if (!modes.contains(request.mode())) {
      return Optional.of("mode " + request.mode());
    }
    for (FilterFeature f : request.filter().features()) {
      if (!filters.contains(f)) {
        return Optional.of("filter " + f);
      }
    }
    if (request.topK() > maxTopK) {
      return Optional.of("topK " + request.topK() + " > " + maxTopK);
    }
    if (request.vector().isPresent()
        && !vectorSpace.map(s -> s.equals(request.vector().get().space())).orElse(false)) {
      return Optional.of("embedding space " + request.vector().get().space());
    }
    return Optional.empty();
  }
}

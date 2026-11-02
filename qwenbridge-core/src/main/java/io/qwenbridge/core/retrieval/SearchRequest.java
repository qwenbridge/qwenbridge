package io.qwenbridge.core.retrieval;

import io.qwenbridge.core.retrieval.filter.Filter;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * One typed provider call (QB-107). The tenant is mandatory and enforced by every provider; it is
 * never a filter the caller could omit.
 *
 * @param text query text for KEYWORD
 * @param vector query embedding for VECTOR
 * @param topK maximum results; bounded by the provider's capabilities
 */
public record SearchRequest(
    String tenant,
    RetrievalMode mode,
    Optional<String> text,
    Optional<QueryVector> vector,
    Filter filter,
    int topK,
    Duration timeout) {

  public SearchRequest {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(mode, "mode must not be null");
    text = text == null ? Optional.empty() : text;
    vector = vector == null ? Optional.empty() : vector;
    filter = filter == null ? Filter.ALL : filter;
    Objects.requireNonNull(timeout, "timeout must not be null");
    if (tenant.isBlank()) {
      throw new IllegalArgumentException("tenant must not be blank");
    }
    if (topK < 1) {
      throw new IllegalArgumentException("topK must be positive");
    }
    if (mode == RetrievalMode.KEYWORD && text.isEmpty()) {
      throw new IllegalArgumentException("keyword search needs text");
    }
    if (mode == RetrievalMode.VECTOR && vector.isEmpty()) {
      throw new IllegalArgumentException("vector search needs a vector");
    }
  }

  /** The same request with another time budget. */
  public SearchRequest withTimeout(Duration timeout) {
    return new SearchRequest(tenant, mode, text, vector, filter, topK, timeout);
  }

  public static SearchRequest keyword(String tenant, String text, Filter filter, int topK) {
    return new SearchRequest(
        tenant,
        RetrievalMode.KEYWORD,
        Optional.of(text),
        Optional.empty(),
        filter,
        topK,
        Duration.ofSeconds(5));
  }

  public static SearchRequest vector(String tenant, QueryVector vector, Filter filter, int topK) {
    return new SearchRequest(
        tenant,
        RetrievalMode.VECTOR,
        Optional.empty(),
        Optional.of(vector),
        filter,
        topK,
        Duration.ofSeconds(5));
  }
}

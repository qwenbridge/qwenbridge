package io.qwenbridge.core.retrieval;

/**
 * The unified search provider SPI (QB-107). The in-memory indexes and the optional OpenSearch
 * backend implement it; it is the contract for any future search adapter (QB-287).
 *
 * <p>Implementations must: enforce the request tenant; check {@link ProviderCapabilities#unmet} and
 * answer {@link SearchOutcome.Unsupported} instead of silently degrading; return typed failures
 * instead of throwing; honour cancellation and the timeout; and report scores as {@link
 * io.qwenbridge.core.confidence.RetrievalScore}.
 */
public interface SearchProvider {

  String id();

  ProviderCapabilities capabilities();

  SearchOutcome search(SearchRequest request, CancellationToken cancellation);
}

package io.qwenbridge.connectors;

import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.*;
import io.qwenbridge.execution.provider.opensearch.*;
import io.qwenbridge.execution.provider.opensearch.client.OpenSearchApiClient;
import io.qwenbridge.execution.provider.opensearch.mapper.OpenSearchResponseMapper;
import io.qwenbridge.execution.provider.opensearch.query.OpenSearchQueryFactory;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * OpenSearch connector: the Commerce backend contract and, since QB-107, the unified search SPI
 * through the shared neutral-filter translation.
 */
@ApplicationScoped
public final class OpenSearchBackend
    implements CommerceBackend, io.qwenbridge.core.retrieval.SearchProvider {
  private final OpenSearchProvider provider;
  private final OpenSearchApiClient api;
  private final OpenSearchProperties properties;
  private final OpenSearchQueryFactory compiler;
  private final OpenSearchResponseMapper mapper;

  public OpenSearchBackend(
      OpenSearchProvider provider,
      @RestClient OpenSearchApiClient api,
      OpenSearchProperties properties,
      OpenSearchQueryFactory compiler,
      OpenSearchResponseMapper mapper) {
    this.provider = provider;
    this.api = api;
    this.properties = properties;
    this.compiler = compiler;
    this.mapper = mapper;
  }

  public String id() {
    return "opensearch";
  }

  public Set<String> modes() {
    return Set.of("KEYWORD", "VECTOR", "HYBRID");
  }

  public boolean configured() {
    return true;
  }

  @Override
  public io.qwenbridge.core.retrieval.ProviderCapabilities capabilities() {
    return ConnectorProviders.keywordCapabilities();
  }

  /** Unified SPI (QB-107): neutral filter as a bool/filter DSL tree; BM25 over the text fields. */
  @Override
  public io.qwenbridge.core.retrieval.SearchOutcome search(
      io.qwenbridge.core.retrieval.SearchRequest request,
      io.qwenbridge.core.retrieval.CancellationToken cancellation) {
    return ConnectorProviders.run(
        id(),
        configured(),
        capabilities(),
        request,
        cancellation,
        () ->
            mapper
                .from(api.search(properties.index(), ConnectorProviders.keywordPayload(request)))
                .results());
  }

  /**
   * The vector side on the unified SPI (QB-087): approximate kNN over the {@code embedding} field
   * with the neutral filter applied inside the kNN search (OpenSearch efficient filtering with the
   * Lucene engine of the seed mapping). {@code space} is the embedding space the operator built the
   * index with; a query vector from another space is refused by the capability check.
   */
  public io.qwenbridge.core.retrieval.SearchProvider vector(
      io.qwenbridge.core.embedding.EmbeddingSpace space) {
    var capabilities =
        new io.qwenbridge.core.retrieval.ProviderCapabilities(
            java.util.EnumSet.of(io.qwenbridge.core.retrieval.RetrievalMode.VECTOR),
            java.util.EnumSet.allOf(io.qwenbridge.core.retrieval.filter.FilterFeature.class),
            Optional.of(space),
            ConnectorProviders.MAX_TOP_K,
            false);
    return new io.qwenbridge.core.retrieval.SearchProvider() {
      @Override
      public String id() {
        return "opensearch-vector";
      }

      @Override
      public io.qwenbridge.core.retrieval.ProviderCapabilities capabilities() {
        return capabilities;
      }

      @Override
      public io.qwenbridge.core.retrieval.SearchOutcome search(
          io.qwenbridge.core.retrieval.SearchRequest request,
          io.qwenbridge.core.retrieval.CancellationToken cancellation) {
        return ConnectorProviders.run(
            id(),
            configured(),
            capabilities,
            request,
            cancellation,
            () -> {
              var values = request.vector().orElseThrow().values();
              var vector = new ArrayList<Double>(values.length);
              for (double v : values) vector.add(v);
              var payload = new HashMap<String, Object>();
              payload.put("size", request.topK());
              payload.put("_source", Map.of("excludes", List.of("embedding")));
              payload.put(
                  "query",
                  Map.of(
                      "knn",
                      Map.of(
                          "embedding",
                          Map.of(
                              "vector",
                              vector,
                              "k",
                              request.topK(),
                              "filter",
                              NeutralFilterTranslator.openSearch(
                                  request.tenant(), request.filter())))));
              return mapper.from(api.search(properties.index(), payload)).results();
            });
      }
    };
  }

  public SearchResultSet search(Query query) {
    validate(query);
    var options = new HashMap<String, Object>();
    options.put("searchMode", query.mode());
    if (!query.embedding().isEmpty()) options.put("embedding", query.embedding());
    var request = new SearchRequest(query.text(), Map.of("commercePlan", query.plan()), options);
    if (query.limit() == 10 && query.offset() == 0) return provider.search(request).results();
    var compiled = compiler.from(request);
    var payload = new HashMap<String, Object>();
    payload.put("query", compiled.query());
    payload.put("size", query.limit());
    payload.put("from", query.offset());
    payload.put(
        "sort",
        List.of(
            Map.of("_score", "desc"),
            Map.of("documentId", Map.of("order", "asc", "unmapped_type", "keyword"))));
    payload.put("_source", Map.of("excludes", List.of("embedding")));
    return mapper.from(api.search(properties.index(), payload)).results();
  }

  public void upsert(String tenant, String id, Map<String, Object> document) {
    var copy = new HashMap<>(document);
    copy.put("tenantId", tenant);
    copy.put("documentId", id);
    api.upsert(
        properties.index(), tenant + "-" + io.qwenbridge.security.ApiIdentity.digest(id), copy);
  }

  public void delete(String tenant, String id) {
    try {
      api.delete(properties.index(), tenant + "-" + io.qwenbridge.security.ApiIdentity.digest(id));
    } catch (jakarta.ws.rs.NotFoundException ignored) {
    }
  }
}

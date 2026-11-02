package io.qwenbridge.connectors;

import io.qwenbridge.commerce.CommerceCompiler;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.memory.InMemoryKeywordIndex;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-process Commerce backend over the Core in-memory keyword index (QB-091). It needs no search
 * service, database or network, so a deployment and its tests can run Commerce search on a local
 * catalog alone.
 *
 * <ul>
 *   <li><b>Commerce contract.</b> Documents are ranked by BM25 over title, brand, category and
 *       description (all documents for a query without terms). The plan is applied with the same
 *       predicate the Commerce result check uses ({@link CommerceCompiler#matches}), so this
 *       backend can never return what that check would reject.
 *   <li><b>Unified SPI (QB-107).</b> The same documents answer neutral {@link Filter} requests with
 *       the reference semantics.
 *   <li>Keyword only. Vector and hybrid modes are declared unsupported rather than approximated.
 *   <li>Data lives in process memory: it is lost on restart and not shared between instances.
 * </ul>
 */
@ApplicationScoped
public final class MemoryCommerceBackend implements CommerceBackend, SearchProvider {
  static final String ID = "memory";
  static final int MAX_DOCUMENTS = 100_000;
  static final List<String> TEXT_FIELDS = List.of("title", "brand", "category", "description");

  private final InMemoryKeywordIndex index = new InMemoryKeywordIndex(ID, MAX_DOCUMENTS, 10_000);
  private final Map<String, Map<String, Object>> documents = new ConcurrentHashMap<>();
  private final java.util.Optional<io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex> vectors;

  /** Keyword only; documents' {@code embedding} fields are ignored. */
  public MemoryCommerceBackend() {
    this(java.util.Optional.empty());
  }

  /**
   * With a vector index in the configured embedding space: an {@code embedding} field of that
   * dimension (as written by catalog sync) is indexed for vector retrieval (QB-087).
   */
  @jakarta.inject.Inject
  public MemoryCommerceBackend(io.qwenbridge.ai.embedding.EmbeddingStages embeddings) {
    this(embeddings.stage().space());
  }

  public MemoryCommerceBackend(
      java.util.Optional<io.qwenbridge.core.embedding.EmbeddingSpace> space) {
    vectors =
        space.map(
            s ->
                new io.qwenbridge.core.retrieval.memory.InMemoryVectorIndex(
                    ID + "-vector", s, MAX_DOCUMENTS, 1_000));
  }

  /** The vector side on the unified SPI, when an embedding space is configured. */
  public java.util.Optional<SearchProvider> vector() {
    return vectors.map(v -> v);
  }

  @Override
  public String id() {
    return ID;
  }

  @Override
  public Set<String> modes() {
    return Set.of("KEYWORD");
  }

  @Override
  public boolean configured() {
    return true;
  }

  private static String key(String tenant, String id) {
    return tenant + "\u0000" + id;
  }

  @Override
  public synchronized void upsert(String tenant, String id, Map<String, Object> document) {
    var copy = new HashMap<>(document);
    copy.put("tenantId", tenant);
    copy.put("documentId", id);
    Object embedding = copy.remove("embedding");
    String text =
        TEXT_FIELDS.stream()
            .map(copy::get)
            .filter(v -> v != null)
            .map(Object::toString)
            .collect(Collectors.joining(" "));
    var fields = NeutralFilterTranslator.fields(copy);
    index.put(new IndexedDocument(tenant, id, text, fields));
    if (vectors.isPresent()) {
      var space = vectors.get().space();
      if (embedding instanceof List<?> values && values.size() == space.dimensions()) {
        double[] vector = new double[values.size()];
        for (int i = 0; i < vector.length; i++) vector[i] = ((Number) values.get(i)).doubleValue();
        vectors.get().put(tenant, id, vector, space, fields);
      } else {
        // A document without a vector in the index's space is not searchable by meaning.
        vectors.get().remove(tenant, id);
      }
    }
    documents.put(key(tenant, id), Map.copyOf(copy));
  }

  @Override
  public synchronized void delete(String tenant, String id) {
    index.remove(tenant, id);
    vectors.ifPresent(v -> v.remove(tenant, id));
    documents.remove(key(tenant, id));
  }

  @Override
  public SearchResultSet search(Query query) {
    validate(query);
    long started = System.nanoTime();
    String tenant = query.plan().tenant();
    var outcome =
        index.search(
            SearchRequest.keyword(tenant, query.text(), Filter.ALL, MAX_TOP_K_FOR_PLAN),
            CancellationToken.create());
    if (!(outcome instanceof SearchOutcome.Success success))
      throw new IllegalStateException("In-memory search failed: " + outcome);
    List<SearchHit> matching = new ArrayList<>();
    for (ScoredDocument hit : success.hits()) {
      var document = documents.get(key(tenant, hit.id()));
      if (document != null && CommerceCompiler.matches(query.plan(), document))
        matching.add(new SearchHit(hit.id(), hit.score().value(), document, Map.of()));
    }
    var page = matching.stream().skip(query.offset()).limit(query.limit()).toList();
    return new SearchResultSet(page, matching.size(), (System.nanoTime() - started) / 1_000_000);
  }

  /** Candidates considered for one Commerce query: the whole tenant up to the index bound. */
  static final int MAX_TOP_K_FOR_PLAN = 10_000;

  @Override
  public ProviderCapabilities capabilities() {
    return index.capabilities();
  }

  @Override
  public SearchOutcome search(SearchRequest request, CancellationToken cancellation) {
    return index.search(request, cancellation);
  }

  /** Number of stored documents, all tenants. */
  public int size() {
    return documents.size();
  }
}

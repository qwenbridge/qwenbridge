package io.qwenbridge.core.retrieval.memory;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.RetrievalMode;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A bounded in-memory keyword search provider (QB-108). It scores with BM25 over the tokenized
 * document text, applies the typed {@link io.qwenbridge.core.retrieval.filter.Filter}, and ranks by
 * score with the document id as a deterministic tie-break. A query without terms browses: every
 * document that satisfies the filter, ordered by id with score 0. Term statistics are kept per
 * tenant, so one tenant's documents never influence another tenant's ranking. It needs no external
 * service and is the default retrieval backend.
 */
public final class InMemoryKeywordIndex implements SearchProvider {

  static final double K1 = 1.2;
  static final double B = 0.75;

  private record Entry(
      IndexedDocument document, Map<String, Integer> termFrequencies, int length) {}

  private static final class TenantIndex {
    final Map<String, Entry> documents = new HashMap<>();
    final Map<String, Integer> documentFrequency = new HashMap<>();
    long totalLength;
  }

  private final String id;
  private final int maxDocuments;
  private final int maxTopK;
  private final Map<String, TenantIndex> tenants = new HashMap<>();
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private int size;

  public InMemoryKeywordIndex(String id, int maxDocuments, int maxTopK) {
    this.id = id;
    this.maxDocuments = maxDocuments;
    this.maxTopK = maxTopK;
    if (maxDocuments < 1 || maxTopK < 1) {
      throw new IllegalArgumentException("limits must be positive");
    }
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public ProviderCapabilities capabilities() {
    return new ProviderCapabilities(
        EnumSet.of(RetrievalMode.KEYWORD),
        EnumSet.allOf(FilterFeature.class),
        Optional.empty(),
        maxTopK,
        true);
  }

  /** Adds or replaces a document. Fails when the index is full. */
  public void put(IndexedDocument document) {
    Map<String, Integer> tf = new HashMap<>();
    List<String> tokens = Tokenizer.tokens(document.text());
    tokens.forEach(t -> tf.merge(t, 1, Integer::sum));
    lock.writeLock().lock();
    try {
      TenantIndex index = tenants.computeIfAbsent(document.tenant(), t -> new TenantIndex());
      Entry previous = index.documents.get(document.id());
      if (previous == null && size >= maxDocuments) {
        throw new IllegalStateException("keyword index is full");
      }
      if (previous != null) {
        unlink(index, previous);
      } else {
        size++;
      }
      Entry entry = new Entry(document, Map.copyOf(tf), tokens.size());
      index.documents.put(document.id(), entry);
      index.totalLength += entry.length();
      tf.keySet().forEach(t -> index.documentFrequency.merge(t, 1, Integer::sum));
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean remove(String tenant, String documentId) {
    lock.writeLock().lock();
    try {
      TenantIndex index = tenants.get(tenant);
      if (index == null) {
        return false;
      }
      Entry entry = index.documents.remove(documentId);
      if (entry == null) {
        return false;
      }
      unlink(index, entry);
      size--;
      return true;
    } finally {
      lock.writeLock().unlock();
    }
  }

  private static void unlink(TenantIndex index, Entry entry) {
    index.totalLength -= entry.length();
    entry
        .termFrequencies()
        .keySet()
        .forEach(t -> index.documentFrequency.computeIfPresent(t, (k, v) -> v > 1 ? v - 1 : null));
  }

  public int size() {
    lock.readLock().lock();
    try {
      return size;
    } finally {
      lock.readLock().unlock();
    }
  }

  @Override
  public SearchOutcome search(SearchRequest request, CancellationToken cancellation) {
    Optional<String> unmet = capabilities().unmet(request);
    if (unmet.isPresent()) {
      return new SearchOutcome.Unsupported(unmet.get());
    }
    long started = System.nanoTime();
    long deadline = started + request.timeout().toNanos();
    List<String> queryTerms =
        new ArrayList<>(new LinkedHashSet<>(Tokenizer.tokens(request.text().orElseThrow())));
    boolean browse = queryTerms.isEmpty(); // Structured-only query: filter and order by id.
    lock.readLock().lock();
    try {
      TenantIndex index = tenants.get(request.tenant());
      if (index == null || index.documents.isEmpty()) {
        return new SearchOutcome.Success(List.of(), 0, elapsed(started));
      }
      int n = index.documents.size();
      double avgLength = (double) index.totalLength / n;
      record Hit(Entry entry, double score) {}
      List<Hit> hits = new ArrayList<>();
      int visited = 0;
      for (Entry entry : index.documents.values()) {
        if ((++visited & 255) == 0) {
          if (cancellation.cancelled()) {
            return new SearchOutcome.Failed(SearchOutcome.Failure.CANCELLED, id);
          }
          if (System.nanoTime() > deadline) {
            return new SearchOutcome.Failed(SearchOutcome.Failure.TIMEOUT, id);
          }
        }
        double score = 0;
        for (String term : queryTerms) {
          Integer tf = entry.termFrequencies().get(term);
          if (tf == null) {
            continue;
          }
          int df = index.documentFrequency.getOrDefault(term, 0);
          double idf = Math.log(1 + (n - df + 0.5) / (df + 0.5));
          score += idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * entry.length() / avgLength));
        }
        if ((browse || score > 0)
            && FilterEvaluator.matches(request.filter(), entry.document().fields())) {
          hits.add(new Hit(entry, score));
        }
      }
      hits.sort(
          Comparator.comparingDouble(Hit::score)
              .reversed()
              .thenComparing(h -> h.entry().document().id()));
      List<ScoredDocument> out = new ArrayList<>();
      for (int i = 0; i < Math.min(request.topK(), hits.size()); i++) {
        Hit h = hits.get(i);
        out.add(
            new ScoredDocument(
                h.entry().document().id(),
                i + 1,
                new RetrievalScore(h.score(), RetrievalScore.Kind.BM25, id),
                h.entry().document().fields()));
      }
      return new SearchOutcome.Success(out, hits.size(), elapsed(started));
    } finally {
      lock.readLock().unlock();
    }
  }

  private static Duration elapsed(long started) {
    return Duration.ofNanos(System.nanoTime() - started);
  }
}

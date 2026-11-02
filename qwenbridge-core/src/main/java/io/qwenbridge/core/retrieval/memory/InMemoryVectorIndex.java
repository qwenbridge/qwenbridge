package io.qwenbridge.core.retrieval.memory;

import io.qwenbridge.core.confidence.RetrievalScore;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.IndexCompatibility;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.RetrievalMode;
import io.qwenbridge.core.retrieval.ScoredDocument;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * A bounded, exact in-memory vector index (QB-109, QB-111). Vectors belong to one {@link
 * EmbeddingSpace}; inserting or querying with another space is rejected, so vectors from different
 * models or dimensions are never compared. Similarity is cosine over unit-normalized vectors,
 * ranked with the document id as a deterministic tie-break. Reads run concurrently; writes are
 * exclusive. Tenants are isolated.
 */
public final class InMemoryVectorIndex implements SearchProvider {

  private record Entry(String id, double[] unit, Map<String, FieldValue> fields) {}

  private final String id;
  private final EmbeddingSpace space;
  private final int maxDocuments;
  private final int maxTopK;
  private final Map<String, Map<String, Entry>> tenants = new HashMap<>();
  private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
  private int size;

  public InMemoryVectorIndex(String id, EmbeddingSpace space, int maxDocuments, int maxTopK) {
    this.id = id;
    this.space = Objects.requireNonNull(space, "space must not be null");
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

  public EmbeddingSpace space() {
    return space;
  }

  /** Whether this index can serve vectors of {@code current} or must be rebuilt. */
  public IndexCompatibility compatibility(EmbeddingSpace current) {
    return IndexCompatibility.check(space, current);
  }

  @Override
  public ProviderCapabilities capabilities() {
    return new ProviderCapabilities(
        EnumSet.of(RetrievalMode.VECTOR),
        EnumSet.allOf(FilterFeature.class),
        Optional.of(space),
        maxTopK,
        true);
  }

  /** Adds or replaces a document vector produced in {@code vectorSpace}. */
  public void put(
      String tenant,
      String documentId,
      double[] vector,
      EmbeddingSpace vectorSpace,
      Map<String, FieldValue> fields) {
    Objects.requireNonNull(tenant, "tenant must not be null");
    Objects.requireNonNull(documentId, "documentId must not be null");
    if (!space.equals(vectorSpace)) {
      throw new IllegalArgumentException(
          "vector space " + vectorSpace + " does not match index space " + space);
    }
    double[] unit = unit(vector);
    lock.writeLock().lock();
    try {
      Map<String, Entry> docs = tenants.computeIfAbsent(tenant, t -> new HashMap<>());
      if (!docs.containsKey(documentId)) {
        if (size >= maxDocuments) {
          throw new IllegalStateException("vector index is full");
        }
        size++;
      }
      docs.put(
          documentId, new Entry(documentId, unit, Map.copyOf(fields == null ? Map.of() : fields)));
    } finally {
      lock.writeLock().unlock();
    }
  }

  public boolean remove(String tenant, String documentId) {
    lock.writeLock().lock();
    try {
      Map<String, Entry> docs = tenants.get(tenant);
      if (docs != null && docs.remove(documentId) != null) {
        size--;
        return true;
      }
      return false;
    } finally {
      lock.writeLock().unlock();
    }
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
    double[] query;
    try {
      query = unit(request.vector().orElseThrow().values());
    } catch (IllegalArgumentException e) {
      return new SearchOutcome.Failed(SearchOutcome.Failure.INVALID_REQUEST, "zero vector");
    }
    long started = System.nanoTime();
    long deadline = started + request.timeout().toNanos();
    lock.readLock().lock();
    try {
      Map<String, Entry> docs = tenants.getOrDefault(request.tenant(), Map.of());
      record Hit(Entry entry, double score) {}
      List<Hit> hits = new ArrayList<>();
      int visited = 0;
      for (Entry entry : docs.values()) {
        if ((++visited & 255) == 0) {
          if (cancellation.cancelled()) {
            return new SearchOutcome.Failed(SearchOutcome.Failure.CANCELLED, id);
          }
          if (System.nanoTime() > deadline) {
            return new SearchOutcome.Failed(SearchOutcome.Failure.TIMEOUT, id);
          }
        }
        if (FilterEvaluator.matches(request.filter(), entry.fields())) {
          hits.add(new Hit(entry, dot(query, entry.unit())));
        }
      }
      hits.sort(
          Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.entry().id()));
      List<ScoredDocument> out = new ArrayList<>();
      for (int i = 0; i < Math.min(request.topK(), hits.size()); i++) {
        Hit h = hits.get(i);
        out.add(
            new ScoredDocument(
                h.entry().id(),
                i + 1,
                new RetrievalScore(h.score(), RetrievalScore.Kind.VECTOR_SIMILARITY, id),
                h.entry().fields()));
      }
      return new SearchOutcome.Success(
          out, hits.size(), Duration.ofNanos(System.nanoTime() - started));
    } finally {
      lock.readLock().unlock();
    }
  }

  private double[] unit(double[] vector) {
    Objects.requireNonNull(vector, "vector must not be null");
    if (vector.length != space.dimensions()) {
      throw new IllegalArgumentException(
          "expected " + space.dimensions() + " dimensions, got " + vector.length);
    }
    double norm = 0;
    for (double v : vector) {
      if (!Double.isFinite(v)) {
        throw new IllegalArgumentException("vector values must be finite");
      }
      norm += v * v;
    }
    if (norm == 0) {
      throw new IllegalArgumentException("zero vector has no direction");
    }
    double length = Math.sqrt(norm);
    double[] unit = new double[vector.length];
    for (int i = 0; i < vector.length; i++) {
      unit[i] = vector[i] / length;
    }
    return unit;
  }

  private static double dot(double[] a, double[] b) {
    double sum = 0;
    for (int i = 0; i < a.length; i++) {
      sum += a[i] * b[i];
    }
    return sum;
  }
}

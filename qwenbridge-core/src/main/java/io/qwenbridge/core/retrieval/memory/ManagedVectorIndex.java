package io.qwenbridge.core.retrieval.memory;

import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.embedding.IndexCompatibility;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.IndexedDocument;
import io.qwenbridge.core.retrieval.ProviderCapabilities;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lifecycle of an in-memory vector index (QB-114): initialize, insert, update, delete, rebuild and
 * close, safe under concurrent use.
 *
 * <p>A rebuild embeds every source document into a fresh index in the stage's current embedding
 * space and swaps it in atomically only when every document was embedded; searches keep using the
 * old index until then, and a failed rebuild leaves it untouched. After {@link #close()} searches
 * answer UNAVAILABLE and writes are refused.
 */
public final class ManagedVectorIndex implements SearchProvider {

  public enum State {
    READY,
    REBUILDING,
    CLOSED
  }

  /** Outcome of a rebuild. */
  public record RebuildResult(
      boolean swapped, int documents, EmbeddingSpace space, String detail) {}

  private final String id;
  private final int maxDocuments;
  private final int maxTopK;
  private final AtomicReference<InMemoryVectorIndex> current;
  private final AtomicReference<State> state = new AtomicReference<>(State.READY);
  private final EmbeddingStage embeddings;

  public ManagedVectorIndex(
      String id, EmbeddingSpace space, EmbeddingStage embeddings, int maxDocuments, int maxTopK) {
    this.id = id;
    this.maxDocuments = maxDocuments;
    this.maxTopK = maxTopK;
    this.embeddings = Objects.requireNonNull(embeddings, "embeddings must not be null");
    this.current = new AtomicReference<>(new InMemoryVectorIndex(id, space, maxDocuments, maxTopK));
  }

  @Override
  public String id() {
    return id;
  }

  public State state() {
    return state.get();
  }

  public EmbeddingSpace space() {
    return current.get().space();
  }

  public int size() {
    return current.get().size();
  }

  /** Whether the index must be rebuilt for the embedding stage's current model. */
  public IndexCompatibility compatibility() {
    return embeddings
        .space()
        .map(s -> IndexCompatibility.check(space(), s))
        .orElse(new IndexCompatibility(true, List.of()));
  }

  @Override
  public ProviderCapabilities capabilities() {
    return current.get().capabilities();
  }

  /** Embeds and inserts (or replaces) documents; returns the failure, if any, for the batch. */
  public EmbeddingResult upsert(List<IndexedDocument> documents) {
    requireOpen();
    EmbeddingResult result =
        embeddings.embed(documents.stream().map(IndexedDocument::text).toList());
    if (result instanceof EmbeddingResult.Embedded embedded) {
      InMemoryVectorIndex index = current.get();
      List<double[]> vectors = embedded.vectors();
      for (int i = 0; i < documents.size(); i++) {
        IndexedDocument d = documents.get(i);
        index.put(d.tenant(), d.id(), vectors.get(i), embedded.space(), d.fields());
      }
    }
    return result;
  }

  public boolean delete(String tenant, String documentId) {
    requireOpen();
    return current.get().remove(tenant, documentId);
  }

  /**
   * Re-embeds every source document into a new index and swaps it in atomically on success.
   *
   * @param batchSize documents per embedding call
   */
  public RebuildResult rebuild(List<IndexedDocument> source, int batchSize) {
    requireOpen();
    if (!state.compareAndSet(State.READY, State.REBUILDING)) {
      return new RebuildResult(false, 0, space(), "a rebuild is already running");
    }
    try {
      EmbeddingSpace target = embeddings.space().orElse(null);
      if (target == null) {
        return new RebuildResult(false, 0, space(), "no embedding provider");
      }
      InMemoryVectorIndex fresh = new InMemoryVectorIndex(id, target, maxDocuments, maxTopK);
      for (int from = 0; from < source.size(); from += batchSize) {
        List<IndexedDocument> batch =
            new ArrayList<>(source.subList(from, Math.min(source.size(), from + batchSize)));
        EmbeddingResult result =
            embeddings.embed(batch.stream().map(IndexedDocument::text).toList());
        if (!(result instanceof EmbeddingResult.Embedded embedded)) {
          return new RebuildResult(
              false,
              0,
              space(),
              "embedding failed: " + ((EmbeddingResult.Failed) result).failure());
        }
        for (int i = 0; i < batch.size(); i++) {
          IndexedDocument d = batch.get(i);
          fresh.put(d.tenant(), d.id(), embedded.vectors().get(i), embedded.space(), d.fields());
        }
      }
      if (state.get() == State.CLOSED) {
        return new RebuildResult(false, 0, space(), "closed during rebuild");
      }
      current.set(fresh);
      return new RebuildResult(true, fresh.size(), target, "rebuilt");
    } finally {
      state.compareAndSet(State.REBUILDING, State.READY);
    }
  }

  public void close() {
    state.set(State.CLOSED);
  }

  @Override
  public SearchOutcome search(SearchRequest request, CancellationToken cancellation) {
    if (state.get() == State.CLOSED) {
      return new SearchOutcome.Failed(SearchOutcome.Failure.UNAVAILABLE, id + " is closed");
    }
    return current.get().search(request, cancellation);
  }

  private void requireOpen() {
    if (state.get() == State.CLOSED) {
      throw new IllegalStateException("vector index " + id + " is closed");
    }
  }
}

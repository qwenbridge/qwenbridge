package io.qwenbridge.core.retrieval.hybrid;

import io.qwenbridge.core.embedding.EmbeddingResult;
import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.embedding.IndexCompatibility;
import io.qwenbridge.core.retrieval.CancellationToken;
import io.qwenbridge.core.retrieval.QueryVector;
import io.qwenbridge.core.retrieval.RetrievalMode;
import io.qwenbridge.core.retrieval.SearchOutcome;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.SearchRequest;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Degradation;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Strategy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Keyword plus vector retrieval with fusion and an explicit partial-failure policy (QB-112,
 * QB-115).
 *
 * <ul>
 *   <li>Both sides run concurrently with the same tenant and filter, each over-fetching so fusion
 *       has candidates; results are fused with {@link RankFusion#rrf}.
 *   <li>If the query cannot be embedded (no provider, unavailable, timeout) or the vector index was
 *       built in another embedding space, the vector side does not run; the result is KEYWORD_ONLY
 *       with the reason recorded.
 *   <li>If one provider fails or is unsupported, the other side's results are returned and the
 *       failure is recorded. If both fail, nothing is returned and both reasons are recorded.
 * </ul>
 */
public final class HybridRetriever {

  private final SearchProvider keyword;
  private final Optional<SearchProvider> vector;
  private final EmbeddingStage embeddings;
  private final int k;

  public HybridRetriever(
      SearchProvider keyword, Optional<SearchProvider> vector, EmbeddingStage embeddings, int k) {
    this.keyword = Objects.requireNonNull(keyword, "keyword must not be null");
    this.vector = vector == null ? Optional.empty() : vector;
    this.embeddings = Objects.requireNonNull(embeddings, "embeddings must not be null");
    this.k = k;
  }

  public HybridResult retrieve(
      String tenant, String text, Filter filter, int topK, Duration timeout, Strategy requested) {
    long started = System.nanoTime();
    List<Degradation> degradations = new ArrayList<>();
    int candidates = Math.max(topK, Math.min(topK * 3, 200));
    CancellationToken cancel = CancellationToken.create();

    boolean wantKeyword = requested != Strategy.VECTOR_ONLY;
    boolean wantVector = requested != Strategy.KEYWORD_ONLY;
    Optional<QueryVector> queryVector = wantVector ? embed(text, degradations) : Optional.empty();

    CompletableFuture<SearchOutcome> keywordCall =
        wantKeyword
            ? call(
                keyword,
                SearchRequest.keyword(tenant, text, filter, candidates).withTimeout(timeout),
                cancel)
            : null;
    CompletableFuture<SearchOutcome> vectorCall =
        queryVector.isPresent() && vector.isPresent()
            ? call(
                vector.get(),
                SearchRequest.vector(tenant, queryVector.get(), filter, candidates)
                    .withTimeout(timeout),
                cancel)
            : null;
    if (wantVector && queryVector.isPresent() && vector.isEmpty()) {
      degradations.add(
          new Degradation("vector", Degradation.Reason.PROVIDER_UNSUPPORTED, "no vector provider"));
    }

    List<RankFusion.SourceList> lists = new ArrayList<>();
    boolean keywordRan = collect(keyword.id(), keywordCall, lists, degradations);
    boolean vectorRan =
        vector.isPresent() && collect(vector.get().id(), vectorCall, lists, degradations);

    Strategy executed =
        keywordRan && vectorRan
            ? Strategy.HYBRID
            : keywordRan ? Strategy.KEYWORD_ONLY : vectorRan ? Strategy.VECTOR_ONLY : Strategy.NONE;
    List<FusedDocument> fused = lists.isEmpty() ? List.of() : RankFusion.rrf(lists, k, topK);
    return new HybridResult(
        requested, executed, fused, degradations, Duration.ofNanos(System.nanoTime() - started));
  }

  private Optional<QueryVector> embed(String text, List<Degradation> degradations) {
    EmbeddingResult result = embeddings.embed(List.of(text));
    if (result instanceof EmbeddingResult.Failed failed) {
      degradations.add(
          new Degradation(
              "embedding", Degradation.Reason.EMBEDDING_UNAVAILABLE, failed.failure().name()));
      return Optional.empty();
    }
    EmbeddingResult.Embedded embedded = (EmbeddingResult.Embedded) result;
    EmbeddingSpace space = embedded.space();
    Optional<EmbeddingSpace> indexSpace = vector.flatMap(v -> v.capabilities().vectorSpace());
    if (indexSpace.isPresent()) {
      IndexCompatibility compatibility = IndexCompatibility.check(indexSpace.get(), space);
      if (compatibility.reindexRequired()) {
        degradations.add(
            new Degradation(
                "vector",
                Degradation.Reason.REINDEX_REQUIRED,
                compatibility.reindexReasons().toString()));
        return Optional.empty();
      }
    }
    return Optional.of(new QueryVector(embedded.vectors().getFirst(), space));
  }

  private static CompletableFuture<SearchOutcome> call(
      SearchProvider provider, SearchRequest request, CancellationToken cancel) {
    CompletableFuture<SearchOutcome> future = new CompletableFuture<>();
    Thread.ofVirtual()
        .name("qwenbridge-retrieval-" + provider.id())
        .start(
            () -> {
              try {
                future.complete(provider.search(request, cancel));
              } catch (RuntimeException e) {
                future.complete(
                    new SearchOutcome.Failed(SearchOutcome.Failure.UNAVAILABLE, provider.id()));
              }
            });
    return future;
  }

  private static boolean collect(
      String source,
      CompletableFuture<SearchOutcome> call,
      List<RankFusion.SourceList> lists,
      List<Degradation> degradations) {
    if (call == null) {
      return false;
    }
    SearchOutcome outcome = call.join();
    switch (outcome) {
      case SearchOutcome.Success s -> {
        lists.add(new RankFusion.SourceList(source, s.hits()));
        return true;
      }
      case SearchOutcome.Unsupported u -> {
        degradations.add(
            new Degradation(source, Degradation.Reason.PROVIDER_UNSUPPORTED, u.requirement()));
        return false;
      }
      case SearchOutcome.Failed f -> {
        degradations.add(
            new Degradation(source, Degradation.Reason.PROVIDER_FAILED, f.failure().name()));
        return false;
      }
    }
  }

  /** The modes a requested strategy needs. */
  static List<RetrievalMode> modes(Strategy s) {
    return switch (s) {
      case HYBRID -> List.of(RetrievalMode.KEYWORD, RetrievalMode.VECTOR);
      case KEYWORD_ONLY -> List.of(RetrievalMode.KEYWORD);
      case VECTOR_ONLY -> List.of(RetrievalMode.VECTOR);
      case NONE -> List.of();
    };
  }
}

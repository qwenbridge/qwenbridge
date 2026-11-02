package io.qwenbridge.core.ranking;

import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Ranking from retrieved candidates to the final order (QB-117 – QB-123): baseline under a
 * versioned policy, optional reranking, then re-validation of every HARD constraint (QB-122).
 * Ranking can reorder; it can never let through a result that violates the HARD filter, add a
 * result that retrieval did not return, or return one twice.
 */
public final class RankingPipeline {

  /**
   * @param removedByValidation items dropped by the final re-validation; non-zero means a provider
   *     returned a violating result
   */
  public record Result(
      String policy,
      List<RankedItem> items,
      RerankStage.Status rerank,
      Optional<String> reranker,
      int removedByValidation) {
    public Result {
      items = List.copyOf(items);
      reranker = reranker == null ? Optional.empty() : reranker;
    }
  }

  private RankingPipeline() {}

  public static Result run(
      RankingPolicy policy,
      List<RankCandidate> candidates,
      List<Filter> preferences,
      Filter hard,
      Optional<Reranker> reranker,
      String query,
      Duration rerankTimeout) {
    List<RankedItem> baseline = BaselineRanker.rank(policy, candidates, preferences);
    RerankStage.Result reranked =
        RerankStage.apply(reranker, query, baseline, policy.rerankWindow(), rerankTimeout);
    Set<String> retrieved = new HashSet<>(candidates.stream().map(RankCandidate::id).toList());
    Set<String> seen = new HashSet<>();
    List<RankedItem> valid = new ArrayList<>();
    int removed = 0;
    for (RankedItem item : reranked.items()) {
      if (retrieved.contains(item.id())
          && seen.add(item.id())
          && FilterEvaluator.matches(hard, item.fields())) {
        valid.add(item.at(valid.size() + 1));
      } else {
        removed++;
      }
    }
    return new Result(policy.key(), valid, reranked.status(), reranked.reranker(), removed);
  }
}

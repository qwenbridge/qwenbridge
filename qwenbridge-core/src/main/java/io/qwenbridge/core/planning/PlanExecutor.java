package io.qwenbridge.core.planning;

import io.qwenbridge.core.embedding.EmbeddingStage;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.filter.FilterEvaluator;
import io.qwenbridge.core.retrieval.hybrid.FusedDocument;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.retrieval.hybrid.HybridRetriever;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Executes a {@link RetrievalPlan} (QB-099, QB-104). Retrieval runs through the {@link
 * HybridRetriever} with the plan's strategy (degradations are recorded, never hidden). The final
 * stage re-checks every result against the plan's HARD filter; anything that does not satisfy it is
 * removed and counted, because a provider that returns such a document is defective.
 */
public final class PlanExecutor {

  private final SearchProvider keyword;
  private final Optional<SearchProvider> vector;
  private final EmbeddingStage embeddings;

  public PlanExecutor(
      SearchProvider keyword, Optional<SearchProvider> vector, EmbeddingStage embeddings) {
    this.keyword = Objects.requireNonNull(keyword, "keyword must not be null");
    this.vector = vector == null ? Optional.empty() : vector;
    this.embeddings = Objects.requireNonNull(embeddings, "embeddings must not be null");
  }

  /**
   * @param removedByValidation results dropped because they violated a HARD constraint
   */
  public record Execution(RetrievalPlan plan, HybridResult result, int removedByValidation) {}

  public Execution execute(RetrievalPlan plan) {
    HybridResult raw =
        new HybridRetriever(keyword, vector, embeddings, 60)
            .retrieve(
                plan.tenant(),
                plan.text(),
                plan.filter(),
                plan.topK(),
                plan.budget().timeout(),
                plan.strategy());
    List<FusedDocument> valid = new ArrayList<>();
    int removed = 0;
    for (FusedDocument d : raw.documents()) {
      if (FilterEvaluator.matches(plan.filter(), d.fields())) {
        valid.add(
            new FusedDocument(d.id(), valid.size() + 1, d.fusedScore(), d.sources(), d.fields()));
      } else {
        removed++;
      }
    }
    return new Execution(
        plan,
        new HybridResult(raw.requested(), raw.executed(), valid, raw.degradations(), raw.took()),
        removed);
  }
}

package io.qwenbridge.commerce;

import io.qwenbridge.ai.embedding.EmbeddingStages;
import io.qwenbridge.connectors.CommerceBackends;
import io.qwenbridge.connectors.MemoryCommerceBackend;
import io.qwenbridge.connectors.OpenSearchBackend;
import io.qwenbridge.core.planning.AdaptivePlanner;
import io.qwenbridge.core.planning.CandidateProvider;
import io.qwenbridge.core.planning.PlanExecutor;
import io.qwenbridge.core.planning.PlanOutcome;
import io.qwenbridge.core.planning.PlanningPolicy;
import io.qwenbridge.core.planning.RetrievalPlan;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.filter.FieldValue;
import io.qwenbridge.core.retrieval.hybrid.HybridResult;
import io.qwenbridge.core.validation.ValidatedQuery;
import io.qwenbridge.execution.provider.model.SearchHit;
import io.qwenbridge.execution.provider.model.SearchResultSet;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Planned retrieval for the {@code commerce-search/1.0} contract (QB-087, QB-090). The Core
 * Adaptive Planner chooses keyword, vector or hybrid retrieval from the QueryIR's needs and the
 * providers the selected backend actually offers; the Core plan executor runs it through the
 * unified search SPI and re-checks every result against the HARD filter. The client never chooses
 * the strategy, and anything that could not run is reported, not hidden.
 *
 * <p>Vector retrieval is available where the backend has a vector side in the configured embedding
 * space: the in-memory backend and the optional OpenSearch backend (kNN). A backend without a
 * vector side reports semantic needs as {@code SEMANTIC_UNAVAILABLE}.
 *
 * <p>Latencies given to the planner are operator estimates for budgeting ({@code
 * qwenbridge.commerce.planning.*}), not measurements.
 */
@ApplicationScoped
public class CommercePlanning {
  /**
   * Results reachable by paging under the 1.0 contract (keyword candidates are three times this).
   */
  public static final int WINDOW = 30;

  @jakarta.inject.Inject CommerceBackends backends;
  @jakarta.inject.Inject OpenSearchBackend opensearch;
  @jakarta.inject.Inject MemoryCommerceBackend memory;
  @jakarta.inject.Inject EmbeddingStages embeddings;
  @jakarta.inject.Inject io.qwenbridge.ranking.policy.RankingPolicies rankingPolicies;

  @ConfigProperty(name = "qwenbridge.commerce.rerank-timeout-ms", defaultValue = "3000")
  long rerankTimeoutMs;

  @ConfigProperty(name = "qwenbridge.commerce.planning.objective", defaultValue = "BALANCED")
  PlanningPolicy.Objective objective;

  @ConfigProperty(name = "qwenbridge.commerce.planning.timeout", defaultValue = "5s")
  Duration timeout;

  @ConfigProperty(name = "qwenbridge.commerce.planning.keyword-latency", defaultValue = "50ms")
  Duration keywordLatency;

  @ConfigProperty(name = "qwenbridge.commerce.planning.vector-latency", defaultValue = "100ms")
  Duration vectorLatency;

  @ConfigProperty(name = "qwenbridge.commerce.planning.embedding-latency", defaultValue = "500ms")
  Duration embeddingLatency;

  /**
   * The outcome of one scope. {@code status} is OK, UNSUPPORTED or UNAVAILABLE (every provider
   * failed: zero results that say nothing about the catalog). {@code found} counts the results
   * within the {@link #WINDOW}; {@code results.totalHits} carries the same number.
   */
  public record Outcome(
      String status, CommerceContracts.Retrieval retrieval, SearchResultSet results, int found) {}

  public Outcome execute(
      String tenant,
      String backendId,
      QueryIR ir,
      ValidatedQuery validated,
      String catalogText,
      int offset,
      int limit,
      Optional<io.qwenbridge.core.ranking.Reranker> reranker) {
    if (offset < 0 || limit < 1 || offset + limit > WINDOW)
      throw new IllegalArgumentException(
          CommerceContracts.SearchRequest.CONTRACT_V1
              + " pages reach at most "
              + WINDOW
              + " results");
    var backend = backends.backend(tenant, backendId);
    if (!(backend instanceof SearchProvider keyword))
      throw new IllegalStateException("Backend " + backend.id() + " is not on the unified SPI");
    Optional<SearchProvider> vector = vectorFor(backend.id());
    var space = embeddings.stage().space();
    var providers =
        new AdaptivePlanner.Providers(
            Optional.of(new CandidateProvider(keyword, keywordLatency)),
            vector.map(v -> new CandidateProvider(v, vectorLatency)),
            space,
            embeddingLatency);
    var policy = new PlanningPolicy(objective, timeout, 2);
    // The whole window is planned and retrieved so the count is exact within it and paging can
    // tell whether more results exist; a page is then a slice of the same ranked window.
    int topK = WINDOW;
    PlanOutcome outcome =
        AdaptivePlanner.plan(tenant, ir, Optional.of(validated), providers, policy, topK);
    if (outcome instanceof PlanOutcome.Unsupported unsupported)
      return new Outcome(
          "UNSUPPORTED",
          new CommerceContracts.Retrieval(
              "NONE",
              "NONE",
              unsupported.reasons().stream().map(Enum::name).toList(),
              List.of(),
              true,
              0,
              List.of(keyword.id()),
              unsupported.requirements(),
              rankingPolicies.policy().key(),
              "none",
              io.qwenbridge.core.recovery.ResultAssessment.Outcome.NOT_EXECUTED.name()),
          SearchResultSet.empty(),
          0);
    RetrievalPlan planned = ((PlanOutcome.Planned) outcome).plan();
    // Retrieval text is the catalog-language text the Commerce flow produced; the planner only
    // decides whether text matters at all (a structured-only query browses by filter).
    RetrievalPlan plan =
        new RetrievalPlan(
            planned.version(),
            planned.tenant(),
            planned.strategy(),
            planned.text().isBlank() ? "" : catalogText,
            planned.filter(),
            planned.preferences(),
            planned.topK(),
            planned.budget(),
            planned.stages(),
            planned.fallback(),
            planned.reasons());
    var execution = new PlanExecutor(keyword, vector, embeddings.stage()).execute(plan);
    HybridResult result = execution.result();
    var documents = result.documents();
    // QB-117 – QB-123: rank under the configured policy (SOFT preferences rank, never filter),
    // optionally rerank the top, then re-validate every HARD constraint.
    var candidates =
        documents.stream()
            .map(
                d ->
                    new io.qwenbridge.core.ranking.RankCandidate(
                        d.id(), d.rank(), d.fusedScore(), d.fields()))
            .toList();
    var preferences =
        validated.preferences().stream()
            .map(io.qwenbridge.core.planning.FilterCompiler::preference)
            .flatMap(Optional::stream)
            .toList();
    var ranking =
        io.qwenbridge.core.ranking.RankingPipeline.run(
            rankingPolicies.policy(),
            candidates,
            preferences,
            plan.filter(),
            reranker,
            catalogText,
            Duration.ofMillis(rerankTimeoutMs));
    var items = ranking.items();
    List<SearchHit> hits = new ArrayList<>();
    for (int i = offset; i < Math.min(items.size(), offset + limit); i++)
      hits.add(hit(items.get(i)));
    List<String> providerIds = new ArrayList<>();
    providerIds.add(keyword.id());
    vector.ifPresent(v -> providerIds.add(v.id()));
    List<String> warnings = new ArrayList<>();
    int removed = execution.removedByValidation() + ranking.removedByValidation();
    if (removed > 0)
      warnings.add(
          "RESULTS_REMOVED_BY_VALIDATION: " + removed + " results violated a hard constraint");
    boolean providerFailed =
        result.executed() == HybridResult.Strategy.NONE
            || result.degradations().stream()
                .anyMatch(d -> d.reason() == HybridResult.Degradation.Reason.PROVIDER_FAILED);
    var assessment =
        io.qwenbridge.core.recovery.ResultAssessment.of(true, providerFailed, items.size(), 0);
    String reranking =
        switch (ranking.rerank()) {
          case DISABLED -> "none";
          case APPLIED -> ranking.reranker().orElse("reranker");
          default -> "baseline:" + ranking.rerank().name();
        };
    boolean degraded = result.degraded() || plannedDegraded(planned);
    return new Outcome(
        assessment.outcome() == io.qwenbridge.core.recovery.ResultAssessment.Outcome.PROVIDER_FAILED
            ? "UNAVAILABLE"
            : "OK",
        new CommerceContracts.Retrieval(
            requested(planned).name(),
            result.executed().name(),
            planned.reasons().stream().map(Enum::name).toList(),
            result.degradations().stream()
                .map(d -> d.component() + ":" + d.reason() + ":" + d.detail())
                .toList(),
            degraded || !"none".equals(reranking) && reranking.startsWith("baseline:"),
            removed,
            providerIds,
            warnings,
            ranking.policy(),
            reranking,
            assessment.outcome().name()),
        new SearchResultSet(hits, items.size(), result.took().toMillis()),
        items.size());
  }

  /** The strategy the query's needs asked for, before availability or budget changed it. */
  static HybridResult.Strategy requested(RetrievalPlan plan) {
    var reasons = plan.reasons();
    boolean downgraded =
        reasons.contains(io.qwenbridge.core.planning.PlanReason.SEMANTIC_UNAVAILABLE)
            || reasons.contains(io.qwenbridge.core.planning.PlanReason.VECTOR_REINDEX_REQUIRED)
            || reasons.contains(io.qwenbridge.core.planning.PlanReason.BUDGET_LIMITED);
    if (!downgraded) return plan.strategy();
    // Only semantic strategies are ever downgraded: hybrid, or vector-only under the latency
    // objective.
    return reasons.contains(io.qwenbridge.core.planning.PlanReason.POLICY_LATENCY)
        ? HybridResult.Strategy.VECTOR_ONLY
        : HybridResult.Strategy.HYBRID;
  }

  private static boolean plannedDegraded(RetrievalPlan plan) {
    return requested(plan) != plan.strategy();
  }

  Optional<SearchProvider> vectorFor(String backendId) {
    var space = embeddings.stage().space();
    if (space.isEmpty()) return Optional.empty();
    return switch (backendId) {
      case "opensearch" -> Optional.of(opensearch.vector(space.get()));
      case "memory" -> memory.vector();
      default -> Optional.empty();
    };
  }

  /**
   * A ranked result in the v2 hit shape. {@code score} is the ranking policy's baseline score
   * (ordinal, comparable within one response only); the retrieval score, the score components and
   * the reranker's position are kept in the metadata (QB-120, QB-123).
   */
  static SearchHit hit(io.qwenbridge.core.ranking.RankedItem item) {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("position", item.position());
    metadata.put("retrievalRank", item.retrievalRank());
    metadata.put(
        "retrievalScore",
        Map.of(
            "value", item.retrievalScore().value(),
            "kind", item.retrievalScore().kind().name(),
            "scorer", item.retrievalScore().scorer()));
    metadata.put(
        "scoreComponents",
        item.components().stream()
            .map(
                c ->
                    Map.of(
                        "name", c.name(),
                        "kind", c.kind().name(),
                        "raw", c.raw(),
                        "contribution", c.contribution(),
                        "source", c.source()))
            .toList());
    item.rerankPosition().ifPresent(p -> metadata.put("rerankPosition", p));
    return new SearchHit(item.id(), item.score(), plainFields(item.fields()), metadata);
  }

  static Map<String, Object> plainFields(Map<String, FieldValue> fields) {
    Map<String, Object> plain = new LinkedHashMap<>();
    fields.forEach((name, value) -> plain.put(name, plain(value)));
    return plain;
  }

  static Object plain(FieldValue value) {
    return switch (value) {
      case FieldValue.Text t -> t.value();
      case FieldValue.Number n -> n.value();
      case FieldValue.Bool b -> b.value();
      case FieldValue.TextList l -> l.values();
    };
  }
}

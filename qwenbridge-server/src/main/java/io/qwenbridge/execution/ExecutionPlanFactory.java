package io.qwenbridge.execution;

import io.qwenbridge.decision.SearchBackend;
import io.qwenbridge.decision.SearchDecision;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ExecutionPlanFactory {

  /**
   * QB-293: the retrieval backend is infrastructure, chosen by configuration ({@code
   * qwenbridge.search.backend}: {@code in-memory} by default, or {@code opensearch}), never by the
   * model's analysis. A direct answer still uses no backend. Without a configured value (unit
   * tests) the decision's backend is kept.
   */
  private final java.util.Optional<SearchBackend> configured;

  public ExecutionPlanFactory() {
    this.configured = java.util.Optional.empty();
  }

  @jakarta.inject.Inject
  public ExecutionPlanFactory(
      @org.eclipse.microprofile.config.inject.ConfigProperty(name = "qwenbridge.search.backend")
          java.util.Optional<String> backend) {
    this.configured = backend.map(ExecutionPlanFactory::parse);
  }

  static SearchBackend parse(String value) {
    return switch (value.strip().toLowerCase(java.util.Locale.ROOT)) {
      case "in-memory", "in_memory", "inmemory", "memory" -> SearchBackend.IN_MEMORY;
      case "opensearch" -> SearchBackend.OPENSEARCH;
      case "custom" -> SearchBackend.CUSTOM;
      default ->
          throw new IllegalArgumentException(
              "qwenbridge.search.backend must be in-memory, opensearch or custom");
    };
  }

  private SearchBackend backend(SearchDecision decision) {
    if (decision.backend() == SearchBackend.NONE) return SearchBackend.NONE;
    return configured.orElse(decision.backend());
  }

  public ExecutionPlan from(SearchDecision decision) {
    List<ExecutionStep> steps = new ArrayList<>();
    int order = 10;

    if (decision.rewriteAgain()) {
      steps.add(
          new ExecutionStep(
              order, ExecutionOperation.REWRITE_QUERY, "Decision requested another rewrite."));
      order += 10;
    }

    if (decision.answer()) {
      steps.add(
          new ExecutionStep(
              order, ExecutionOperation.DIRECT_ANSWER, "Decision requested direct answer."));
      return ExecutionPlan.builder()
          .mode(decision.mode())
          .backend(backend(decision))
          .steps(steps)
          .reason(decision.reason())
          .build();
    }

    if (decision.hybridSearch()) {
      steps.add(
          new ExecutionStep(
              order, ExecutionOperation.HYBRID_SEARCH, "Decision requested hybrid search."));
      order += 10;
    } else {
      if (decision.keywordSearch()) {
        steps.add(
            new ExecutionStep(
                order, ExecutionOperation.KEYWORD_SEARCH, "Decision requested keyword search."));
        order += 10;
      }

      if (decision.vectorSearch()) {
        steps.add(
            new ExecutionStep(
                order, ExecutionOperation.VECTOR_SEARCH, "Decision requested vector search."));
        order += 10;
      }
    }

    if (decision.facets()) {
      steps.add(
          new ExecutionStep(order, ExecutionOperation.APPLY_FACETS, "Decision requested facets."));
      order += 10;
    }

    if (decision.rerank()) {
      steps.add(
          new ExecutionStep(
              order, ExecutionOperation.RERANK_RESULTS, "Decision requested reranking."));
      order += 10;
    }

    steps.add(
        new ExecutionStep(order, ExecutionOperation.RETURN_RESULTS, "Return search results."));

    return ExecutionPlan.builder()
        .mode(decision.mode())
        .backend(backend(decision))
        .steps(steps)
        .reason(decision.reason())
        .build();
  }
}

package io.qwenbridge.intelligence.runtime;

import io.qwenbridge.execution.provider.implementation.OpenSearchProvider;
import io.qwenbridge.execution.provider.model.SearchRequest;
import io.qwenbridge.execution.provider.model.SearchResponse;
import io.qwenbridge.intelligence.extraction.QueryEntityConstraintExtractor;
import io.qwenbridge.intelligence.ir.QueryIRMapper;
import io.qwenbridge.intelligence.planning.LogicalQueryPlan;
import io.qwenbridge.intelligence.planning.LogicalQueryPlanner;
import io.qwenbridge.semantic.SemanticAnalysis;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Opt-in bridge from semantic analysis to OpenSearch structured keyword execution. Callers must
 * enforce authorization and threat/policy checks before invoking this service. No existing search
 * pipeline behavior is changed by this class.
 */
@ApplicationScoped
public class StructuredQueryCoordinator {
  private final QueryEntityConstraintExtractor extractor;
  private final QueryIRMapper mapper;
  private final LogicalQueryPlanner planner;
  private final OpenSearchProvider provider;

  public StructuredQueryCoordinator(
      QueryEntityConstraintExtractor extractor,
      QueryIRMapper mapper,
      LogicalQueryPlanner planner,
      OpenSearchProvider provider) {
    this.extractor = Objects.requireNonNull(extractor, "extractor");
    this.mapper = Objects.requireNonNull(mapper, "mapper");
    this.planner = Objects.requireNonNull(planner, "planner");
    this.provider = Objects.requireNonNull(provider, "provider");
  }

  public LogicalQueryPlan prepare(SemanticAnalysis analysis) {
    Objects.requireNonNull(analysis, "analysis");
    return planner.plan(mapper.map(analysis.originalQuery(), extractor.extract(analysis)));
  }

  /**
   * Executes only when a supported, grounded price filter exists. Fails closed otherwise. Mapping
   * fields are trusted application configuration, never user-provided text.
   */
  public SearchResponse execute(
      SemanticAnalysis analysis, String configuredPriceField, String configuredCurrencyField) {
    LogicalQueryPlan plan = prepare(analysis);
    StructuredExecutionLimits.validate(plan);
    if (plan.priceUpperBounds().isEmpty()) {
      throw new IllegalArgumentException("No structured price constraint to execute");
    }
    var currencies =
        plan.priceUpperBounds().stream()
            .map(LogicalQueryPlan.PriceUpperBound::currency)
            .collect(Collectors.toSet());
    if (currencies.size() > 1) {
      throw new IllegalArgumentException(
          "Structured search cannot combine price bounds in different currencies");
    }
    return provider.searchStructured(
        SearchRequest.keyword(plan.originalQuery()),
        plan,
        configuredPriceField,
        configuredCurrencyField);
  }
}

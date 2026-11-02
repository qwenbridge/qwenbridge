package io.qwenbridge.core.planning;

import io.qwenbridge.core.embedding.EmbeddingSpace;
import io.qwenbridge.core.planning.RetrievalPlan.Stage;
import io.qwenbridge.core.queryir.Constraint;
import io.qwenbridge.core.queryir.QueryIR;
import io.qwenbridge.core.queryir.RetrievalHints;
import io.qwenbridge.core.queryir.Span;
import io.qwenbridge.core.retrieval.SearchProvider;
import io.qwenbridge.core.retrieval.filter.Filter;
import io.qwenbridge.core.retrieval.filter.FilterFeature;
import io.qwenbridge.core.retrieval.hybrid.HybridResult.Strategy;
import io.qwenbridge.core.validation.ValidatedQuery;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Chooses keyword, vector or hybrid retrieval from what the query needs, what the providers can do
 * and the policy (QB-101–QB-105). Every plan states its reasons; nothing depends on a client- or
 * model-chosen mode.
 *
 * <ol>
 *   <li>HARD constraints are compiled; if any cannot be compiled the outcome is Unsupported.
 *   <li>Needs come from the QueryIR: retrieval hints (EXACT, LEXICAL, SEMANTIC, STRUCTURED) and
 *       semantic signals. Text left after removing compiled constraint spans decides whether text
 *       matching adds anything.
 *   <li>A provider is usable only if it supports the filter's features, the mode, the requested
 *       candidates and (for vectors) the current embedding space.
 *   <li>The policy picks among usable strategies; the budget can only reduce the number of calls.
 *       If the desired strategy is not possible, a usable one is chosen and the reason recorded.
 * </ol>
 */
public final class AdaptivePlanner {

  private AdaptivePlanner() {}

  /** What the planner may use. */
  public record Providers(
      Optional<CandidateProvider> keyword,
      Optional<CandidateProvider> vector,
      Optional<EmbeddingSpace> embeddingSpace,
      Duration embeddingLatency) {
    public Providers {
      keyword = keyword == null ? Optional.empty() : keyword;
      vector = vector == null ? Optional.empty() : vector;
      embeddingSpace = embeddingSpace == null ? Optional.empty() : embeddingSpace;
      embeddingLatency = embeddingLatency == null ? Duration.ZERO : embeddingLatency;
    }
  }

  public static PlanOutcome plan(
      String tenant,
      QueryIR ir,
      Optional<ValidatedQuery> validated,
      Providers providers,
      PlanningPolicy policy,
      int topK) {
    Filter filter = Filter.ALL;
    List<Constraint> compiledConstraints = List.of();
    List<Constraint> preferences = List.of();
    if (validated.isPresent()) {
      FilterCompiler.Result compiled = FilterCompiler.compile(validated.get());
      if (compiled instanceof FilterCompiler.Result.NotCompilable nc) {
        return new PlanOutcome.Unsupported(nc.reasons(), List.of(PlanReason.CAPABILITY_MISMATCH));
      }
      filter = ((FilterCompiler.Result.Compiled) compiled).filter();
      Set<String> ids = Set.copyOf(((FilterCompiler.Result.Compiled) compiled).constraintIds());
      compiledConstraints =
          validated.get().filters().stream().filter(c -> ids.contains(c.id())).toList();
      preferences = validated.get().preferences();
    }

    String text = residualText(ir.originalText(), compiledConstraints);
    RetrievalHints hints = ir.retrievalHints();
    boolean structuredOnly = text.isBlank();
    boolean exact = hints.modes().contains(RetrievalHints.Mode.EXACT);
    boolean semantic =
        !structuredOnly
            && (hints.modes().contains(RetrievalHints.Mode.SEMANTIC)
                || !ir.semanticSignals().isEmpty());
    boolean lexical =
        !structuredOnly
            && (exact || hints.modes().contains(RetrievalHints.Mode.LEXICAL) || !semantic);

    List<PlanReason> reasons = new ArrayList<>();
    List<String> unmet = new ArrayList<>();
    int candidates = Math.min(topK * 3, 200);
    boolean keywordOk =
        usable(providers.keyword(), filter, Optional.empty(), Math.max(candidates, topK), unmet);
    boolean vectorOk = false;
    if (providers.vector().isPresent() && providers.embeddingSpace().isPresent()) {
      Optional<EmbeddingSpace> indexSpace =
          providers.vector().get().provider().capabilities().vectorSpace();
      if (indexSpace.isPresent() && !indexSpace.get().equals(providers.embeddingSpace().get())) {
        reasons.add(PlanReason.VECTOR_REINDEX_REQUIRED);
      } else {
        vectorOk =
            usable(
                providers.vector(),
                filter,
                providers.embeddingSpace(),
                Math.max(candidates, topK),
                unmet);
      }
    }

    Strategy desired;
    if (structuredOnly) {
      desired = Strategy.KEYWORD_ONLY;
      reasons.add(PlanReason.STRUCTURED_ONLY);
    } else {
      switch (policy.objective()) {
        case LATENCY -> {
          reasons.add(PlanReason.POLICY_LATENCY);
          desired = semantic && !lexical ? Strategy.VECTOR_ONLY : Strategy.KEYWORD_ONLY;
        }
        case QUALITY -> {
          reasons.add(PlanReason.POLICY_QUALITY);
          desired = exact ? Strategy.KEYWORD_ONLY : Strategy.HYBRID;
        }
        default ->
            desired =
                exact ? Strategy.KEYWORD_ONLY : semantic ? Strategy.HYBRID : Strategy.KEYWORD_ONLY;
      }
      reasons.add(
          exact
              ? PlanReason.EXACT_IDENTIFIERS
              : semantic && lexical
                  ? PlanReason.BOTH_NEEDS
                  : semantic ? PlanReason.SEMANTIC_NEED : PlanReason.LEXICAL_NEED);
    }

    Strategy chosen = desired;
    if (chosen == Strategy.HYBRID) {
      long latency =
          providers.keyword().map(p -> p.expectedLatency().toMillis()).orElse(0L)
              + providers.vector().map(p -> p.expectedLatency().toMillis()).orElse(0L)
              + providers.embeddingLatency().toMillis();
      if (policy.maxProviderCalls() < 2 || latency > policy.timeout().toMillis()) {
        reasons.add(PlanReason.BUDGET_LIMITED);
        chosen = semantic && !lexical && vectorOk ? Strategy.VECTOR_ONLY : Strategy.KEYWORD_ONLY;
      }
    }
    if ((chosen == Strategy.HYBRID || chosen == Strategy.VECTOR_ONLY) && !vectorOk) {
      if (!reasons.contains(PlanReason.VECTOR_REINDEX_REQUIRED)) {
        reasons.add(PlanReason.SEMANTIC_UNAVAILABLE);
      }
      chosen = Strategy.KEYWORD_ONLY;
    }
    if (chosen == Strategy.HYBRID && !keywordOk) {
      reasons.add(PlanReason.CAPABILITY_MISMATCH);
      chosen = Strategy.VECTOR_ONLY;
    }
    if (chosen == Strategy.KEYWORD_ONLY && !keywordOk) {
      if (vectorOk && !structuredOnly && !exact) {
        reasons.add(PlanReason.CAPABILITY_MISMATCH);
        chosen = Strategy.VECTOR_ONLY;
      } else {
        unmet.add("keyword provider");
        return new PlanOutcome.Unsupported(unmet, reasons);
      }
    }

    List<Stage> stages = new ArrayList<>();
    Duration timeout = policy.timeout();
    if (chosen != Strategy.KEYWORD_ONLY) {
      stages.add(new Stage(Stage.Kind.EMBED_QUERY, Optional.empty(), 1, timeout));
    }
    int fetch = chosen == Strategy.HYBRID ? Math.max(candidates, topK) : topK;
    if (chosen != Strategy.VECTOR_ONLY) {
      stages.add(
          new Stage(
              Stage.Kind.KEYWORD_RETRIEVAL,
              providers.keyword().map(p -> p.provider().id()),
              fetch,
              timeout));
    }
    if (chosen != Strategy.KEYWORD_ONLY) {
      stages.add(
          new Stage(
              Stage.Kind.VECTOR_RETRIEVAL,
              providers.vector().map(p -> p.provider().id()),
              fetch,
              timeout));
    }
    if (chosen == Strategy.HYBRID) {
      stages.add(new Stage(Stage.Kind.FUSE, Optional.empty(), topK, timeout));
    }
    stages.add(new Stage(Stage.Kind.VALIDATE_HARD_CONSTRAINTS, Optional.empty(), topK, timeout));
    Optional<Strategy> fallback =
        chosen != Strategy.KEYWORD_ONLY && keywordOk
            ? Optional.of(Strategy.KEYWORD_ONLY)
            : Optional.empty();
    return new PlanOutcome.Planned(
        new RetrievalPlan(
            RetrievalPlan.VERSION,
            tenant,
            chosen,
            structuredOnly ? "" : text,
            filter,
            preferences,
            topK,
            new RetrievalPlan.Budget(timeout, policy.maxProviderCalls()),
            stages,
            fallback,
            reasons));
  }

  /** Whether a provider can serve the mode, filter and candidates (QB-103). */
  private static boolean usable(
      Optional<CandidateProvider> candidate,
      Filter filter,
      Optional<EmbeddingSpace> space,
      int candidates,
      List<String> unmet) {
    if (candidate.isEmpty()) {
      return false;
    }
    SearchProvider p = candidate.get().provider();
    var caps = p.capabilities();
    for (FilterFeature f : filter.features()) {
      if (!caps.filters().contains(f)) {
        unmet.add(p.id() + ": filter " + f);
        return false;
      }
    }
    if (candidates > caps.maxTopK()) {
      unmet.add(p.id() + ": topK " + candidates);
      return false;
    }
    if (space.isPresent() && !caps.vectorSpace().equals(space)) {
      unmet.add(p.id() + ": embedding space");
      return false;
    }
    return true;
  }

  /** The query text without the spans of constraints that are now filters. */
  static String residualText(String original, List<Constraint> compiled) {
    List<Span> spans =
        compiled.stream()
            .flatMap(c -> c.span().stream())
            .sorted(Comparator.comparingInt(Span::start))
            .toList();
    StringBuilder out = new StringBuilder();
    int at = 0;
    for (Span s : spans) {
      if (s.start() >= at) {
        out.append(original, at, s.start()).append(' ');
        at = s.end();
      }
    }
    out.append(original.substring(Math.min(at, original.length())));
    return out.toString().replaceAll("[\\s,.;:]+", " ").trim();
  }
}

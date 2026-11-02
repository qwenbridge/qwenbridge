package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.event.model.PipelineStage;
import io.qwenbridge.execution.ExecutionEngine;
import io.qwenbridge.execution.ExecutionPlan;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.execution.ExecutionResult;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.ExecutionPlanResult;
import io.qwenbridge.pipeline.result.ExecutionResultResult;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;

@ApplicationScoped
@RequiredArgsConstructor
public class DecisionStep implements PipelineStep<DecisionResult> {

  private final ExecutionPlanFactory executionPlanFactory;
  private final ExecutionEngine executionEngine;

  @Override
  public PipelineStage stage() {
    return PipelineStage.DECISION;
  }

  @Override
  public String name() {
    return "DecisionStep";
  }

  @Override
  public int order() {
    return 80;
  }

  @Override
  public Class<DecisionResult> resultType() {
    return DecisionResult.class;
  }

  @Override
  public DecisionResult execute(ExecutionContext context) {
    // Deterministic assessment first (QB-026/027): vague or out-of-domain requests are clarified
    // with focused questions instead of being executed on fabricated details.
    io.qwenbridge.core.intent.IntentUnderstanding understanding =
        context.get(io.qwenbridge.core.intent.IntentUnderstanding.class);
    if (understanding != null && !understanding.assessment().executable()) {
      context.store(
          ConstraintQuestions.class,
          new ConstraintQuestions(understanding.assessment().questions()));
      return new DecisionResult(DecisionType.CLARIFY);
    }
    SearchAnalysis analysis = context.get(SearchAnalysis.class);
    if (analysis != null
        && "ALLOW".equals(analysis.modelThreatDecision())
        && (!analysis.semanticValidated()
            || !analysis.ambiguities().isEmpty()
            || analysis.intent() == io.qwenbridge.intent.IntentType.UNKNOWN
            || analysis.intentConfidence() < 0.6)) return new DecisionResult(DecisionType.CLARIFY);
    try {
      io.qwenbridge.execution.provider.model.SearchRequestFactory.from(context);
    } catch (IllegalArgumentException ex) {
      context.store(
          ConstraintQuestions.class, new ConstraintQuestions(java.util.List.of(ex.getMessage())));
      return new DecisionResult(DecisionType.CLARIFY);
    }
    SearchDecision decision =
        analysis == null ? SearchDecision.keyword() : analysis.toSearchDecision();

    ExecutionPlan plan = executionPlanFactory.from(decision);
    ExecutionResult executionResult = executionEngine.execute(plan, context);

    context.store(ExecutionPlanResult.class, ExecutionPlanResult.builder().plan(plan).build());
    context.store(
        ExecutionResultResult.class,
        ExecutionResultResult.builder().result(executionResult).build());

    return new DecisionResult(resolveDecisionType(decision));
  }

  public record ConstraintQuestions(java.util.List<String> questions) {
    public ConstraintQuestions {
      questions = java.util.List.copyOf(questions);
    }
  }

  private DecisionType resolveDecisionType(SearchDecision decision) {
    if (decision.rewriteAgain()) {
      return DecisionType.REWRITE;
    }

    return DecisionType.ALLOW;
  }
}

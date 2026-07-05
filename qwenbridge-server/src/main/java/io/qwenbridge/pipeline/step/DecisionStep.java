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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
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
    SearchAnalysis analysis = context.get(SearchAnalysis.class);
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

  private DecisionType resolveDecisionType(SearchDecision decision) {
    if (decision.rewriteAgain()) {
      return DecisionType.REWRITE;
    }

    return DecisionType.ALLOW;
  }
}

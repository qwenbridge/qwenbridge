package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.execution.ExecutionEngine;
import io.qwenbridge.execution.ExecutionPlan;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.execution.ExecutionResult;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.ExecutionPlanResult;
import io.qwenbridge.pipeline.result.ExecutionResultResult;
import org.springframework.stereotype.Component;

@Component
public class DecisionStep implements PipelineStep<DecisionResult> {

    private final DecisionService decisionService;
    private final ExecutionPlanFactory executionPlanFactory;
    private final ExecutionEngine executionEngine;

    public DecisionStep(
            DecisionService decisionService,
            ExecutionPlanFactory executionPlanFactory,
            ExecutionEngine executionEngine
    ) {
        this.decisionService = decisionService;
        this.executionPlanFactory = executionPlanFactory;
        this.executionEngine = executionEngine;
    }

    public String name() { return "DecisionStep"; }
    public int order() { return 80; }
    public Class<DecisionResult> resultType() { return DecisionResult.class; }

    public DecisionResult execute(ExecutionContext context) {
        SearchAnalysis searchAnalysis = context.get(SearchAnalysis.class);
        SearchDecision decision = searchAnalysis != null
                ? searchAnalysis.toSearchDecision()
                : decisionService.decide(context);
        ExecutionPlan plan = executionPlanFactory.from(decision);
        ExecutionResult executionResult = executionEngine.execute(plan, context);

        context.store(ExecutionPlanResult.class, new ExecutionPlanResult(plan));
        context.store(ExecutionResultResult.class, new ExecutionResultResult(executionResult));

        return new DecisionResult(resolveDecisionType(decision));
    }

    private DecisionType resolveDecisionType(SearchDecision decision) {
        if (decision.rewriteAgain()) {
            return DecisionType.REWRITE;
        }
        return DecisionType.ALLOW;
    }
}

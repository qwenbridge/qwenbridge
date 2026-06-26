package io.qwenbridge.pipeline.step;

import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.execution.ExecutionPlan;
import io.qwenbridge.execution.ExecutionPlanFactory;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.ExecutionPlanResult;
import org.springframework.stereotype.Component;

@Component
public class DecisionStep implements PipelineStep<DecisionResult> {

    private final DecisionService decisionService;
    private final ExecutionPlanFactory executionPlanFactory;

    public DecisionStep(
            DecisionService decisionService,
            ExecutionPlanFactory executionPlanFactory
    ) {
        this.decisionService = decisionService;
        this.executionPlanFactory = executionPlanFactory;
    }

    public String name() { return "DecisionStep"; }
    public int order() { return 70; }
    public Class<DecisionResult> resultType() { return DecisionResult.class; }

    public DecisionResult execute(ExecutionContext context) {
        SearchDecision decision = decisionService.decide(context);
        ExecutionPlan plan = executionPlanFactory.from(decision);

        context.store(ExecutionPlanResult.class, new ExecutionPlanResult(plan));

        return new DecisionResult(resolveDecisionType(decision));
    }

    private DecisionType resolveDecisionType(SearchDecision decision) {
        if (decision.rewriteAgain()) {
            return DecisionType.REWRITE;
        }
        return DecisionType.ALLOW;
    }
}

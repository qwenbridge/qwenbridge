package io.qwenbridge.pipeline.step;

import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.decision.DecisionType;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.DecisionResult;
import org.springframework.stereotype.Component;

@Component
public class DecisionStep implements PipelineStep<DecisionResult> {

    private final DecisionService decisionService;

    public DecisionStep(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    public String name() { return "DecisionStep"; }
    public int order() { return 70; }
    public Class<DecisionResult> resultType() { return DecisionResult.class; }

    public DecisionResult execute(ExecutionContext context) {
        SearchDecision decision = decisionService.decide(context);
        return new DecisionResult(resolveDecisionType(decision));
    }

    private DecisionType resolveDecisionType(SearchDecision decision) {
        if (decision.rewriteAgain()) {
            return DecisionType.REWRITE;
        }

        return DecisionType.ALLOW;
    }
}

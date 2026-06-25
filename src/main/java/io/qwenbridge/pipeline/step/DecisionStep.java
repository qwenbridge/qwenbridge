package io.qwenbridge.pipeline.step;

import io.qwenbridge.decision.DecisionService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import io.qwenbridge.pipeline.result.DecisionResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.springframework.stereotype.Component;

@Component
public class DecisionStep implements PipelineStep<DecisionResult> {

    private final DecisionService decisionService;

    public DecisionStep(DecisionService decisionService) {
        this.decisionService = decisionService;
    }

    public String name() { return "DecisionStep"; }
    public int order() { return 80; }
    public Class<DecisionResult> resultType() { return DecisionResult.class; }

    public DecisionResult execute(ExecutionContext context) {
        return new DecisionResult(
                decisionService.decide(
                        context.get(ConfidenceResult.class).value(),
                        context.get(RewriteResult.class).rewrites()
                )
        );
    }
}

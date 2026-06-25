package io.omnisearch.pipeline.step;

import io.omnisearch.decision.DecisionService;
import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.ConfidenceResult;
import io.omnisearch.pipeline.result.DecisionResult;
import io.omnisearch.pipeline.result.RewriteResult;
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

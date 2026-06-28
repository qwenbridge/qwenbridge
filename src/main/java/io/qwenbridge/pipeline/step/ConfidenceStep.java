package io.qwenbridge.pipeline.step;

import io.qwenbridge.confidence.ConfidenceService;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.ConfidenceResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import org.springframework.stereotype.Component;

@Component
public class ConfidenceStep implements PipelineStep<ConfidenceResult> {

    private final ConfidenceService confidenceService;

    public ConfidenceStep(ConfidenceService confidenceService) {
        this.confidenceService = confidenceService;
    }

    public String name() { return "ConfidenceStep"; }
    public int order() {
        return 90;
    }
    public Class<ConfidenceResult> resultType() { return ConfidenceResult.class; }

    public ConfidenceResult execute(ExecutionContext context) {
        return new ConfidenceResult(
                confidenceService.calculate(
                        context.request().originalQuery(),
                        context.get(RewriteResult.class).rewrites()
                )
        );
    }
}

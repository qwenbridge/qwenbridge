package io.qwenbridge.pipeline.step;

import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.IntentResult;
import io.qwenbridge.pipeline.result.LanguageResult;
import io.qwenbridge.pipeline.result.RewriteResult;
import io.qwenbridge.rewrite.RewriteService;
import org.springframework.stereotype.Component;

@Component
public class RewriteStep implements PipelineStep<RewriteResult> {

    private final RewriteService rewriteService;

    public RewriteStep(RewriteService rewriteService) {
        this.rewriteService = rewriteService;
    }

    public String name() { return "RewriteStep"; }
    public int order() { return 40; }
    public Class<RewriteResult> resultType() { return RewriteResult.class; }

    public RewriteResult execute(ExecutionContext context) {
        var rewrites = rewriteService.rewrite(
                context.request().originalQuery(),
                context.get(LanguageResult.class).language(),
                context.get(IntentResult.class).intent()
        );

        return new RewriteResult(
                !rewrites.isEmpty(),
                "ai",
                rewrites
        );
    }
}

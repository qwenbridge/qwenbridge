package io.omnisearch.pipeline.step;

import io.omnisearch.pipeline.ExecutionContext;
import io.omnisearch.pipeline.result.RewriteResult;
import io.omnisearch.pipeline.result.SemanticResult;
import org.springframework.stereotype.Component;

@Component
public class SemanticStep implements PipelineStep<SemanticResult> {

    public String name() { return "SemanticStep"; }
    public int order() { return 50; }
    public Class<SemanticResult> resultType() { return SemanticResult.class; }

    public SemanticResult execute(ExecutionContext context) {
        int rewriteCount = context.get(RewriteResult.class).rewrites().size();
        return new SemanticResult(true, rewriteCount > 1 ? 0.96 : 0.85);
    }
}

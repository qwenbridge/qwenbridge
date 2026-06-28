package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.pipeline.ExecutionContext;
import io.qwenbridge.pipeline.result.SemanticResult;
import io.qwenbridge.semantic.SemanticAnalysis;
import io.qwenbridge.semantic.SemanticService;
import org.springframework.stereotype.Component;

@Component
public class SemanticStep implements PipelineStep<SemanticResult> {

    private final SemanticService semanticService;

    public SemanticStep(SemanticService semanticService) {
        this.semanticService = semanticService;
    }

    public String name() { return "SemanticStep"; }
    public int order() { return 60; }
    public Class<SemanticResult> resultType() { return SemanticResult.class; }

    public SemanticResult execute(ExecutionContext context) {
        SearchAnalysis searchAnalysis = context.get(SearchAnalysis.class);
        if (searchAnalysis != null) {
            return searchAnalysis.toSemanticResult(context.request().originalQuery());
        }

        SemanticAnalysis analysis = semanticService.analyze(context.request().originalQuery());
        return SemanticResult.validated(analysis);
    }
}

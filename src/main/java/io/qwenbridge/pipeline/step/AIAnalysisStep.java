package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.pipeline.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class AIAnalysisStep implements PipelineStep<SearchAnalysis> {

    private final SearchAnalysisService searchAnalysisService;

    public AIAnalysisStep(SearchAnalysisService searchAnalysisService) {
        this.searchAnalysisService = searchAnalysisService;
    }

    @Override
    public String name() {
        return "AIAnalysisStep";
    }

    @Override
    public int order() {
        return 25;
    }

    @Override
    public Class<SearchAnalysis> resultType() {
        return SearchAnalysis.class;
    }

    @Override
    public SearchAnalysis execute(ExecutionContext context) {
        NormalizedInput normalizedInput = context.get(NormalizedInput.class);
        String query = normalizedInput == null
                ? context.request().originalQuery()
                : normalizedInput.normalizedQuery();

        return searchAnalysisService.analyze(query);
    }
}

package io.qwenbridge.pipeline.step;

import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTraceHolder;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import io.qwenbridge.normalization.model.NormalizedInput;
import io.qwenbridge.pipeline.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class AIAnalysisStep implements PipelineStep<SearchAnalysis> {

    private final SearchAnalysisService searchAnalysisService;
    private final AIAnalysisCacheTraceHolder cacheTraceHolder;

    public AIAnalysisStep(
            SearchAnalysisService searchAnalysisService,
            AIAnalysisCacheTraceHolder cacheTraceHolder
    ) {
        this.searchAnalysisService = searchAnalysisService;
        this.cacheTraceHolder = cacheTraceHolder;
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
        try {
            NormalizedInput normalizedInput = context.get(NormalizedInput.class);
            String query = normalizedInput == null
                    ? context.request().originalQuery()
                    : normalizedInput.normalizedQuery();

            SearchAnalysis analysis = searchAnalysisService.analyze(query);

            AIAnalysisCacheTrace trace = cacheTraceHolder.get();
            context.store(AIAnalysisCacheTrace.class, trace);

            return analysis;
        } finally {
            cacheTraceHolder.clear();
        }
    }
}

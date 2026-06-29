package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.AIAnalysisCacheKeyBuilder;
import io.qwenbridge.analysis.cache.CacheKey;
import io.qwenbridge.analysis.cache.config.AIAnalysisCacheProperties;
import io.qwenbridge.analysis.cache.coalescing.AIAnalysisSingleFlight;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTrace;
import io.qwenbridge.analysis.cache.trace.AIAnalysisCacheTraceHolder;
import io.qwenbridge.analysis.model.SearchAnalysis;
import io.qwenbridge.analysis.parser.SearchAnalysisJsonParser;
import io.qwenbridge.analysis.prompt.SearchAnalysisPromptBuilder;
import io.qwenbridge.analysis.service.SearchAnalysisService;
import org.springframework.stereotype.Service;

@Service
public class QwenSearchAnalysisService implements SearchAnalysisService {

    private final AIService aiService;
    private final SearchAnalysisPromptBuilder promptBuilder;
    private final SearchAnalysisJsonParser parser;
    private final AIAnalysisCache cache;
    private final AIAnalysisCacheKeyBuilder cacheKeyBuilder;
    private final AIAnalysisCacheProperties cacheProperties;
    private final AIAnalysisCacheTraceHolder cacheTraceHolder;
    private final AIAnalysisSingleFlight singleFlight;

    public QwenSearchAnalysisService(
            AIService aiService,
            SearchAnalysisPromptBuilder promptBuilder,
            SearchAnalysisJsonParser parser,
            AIAnalysisCache cache,
            AIAnalysisCacheKeyBuilder cacheKeyBuilder,
            AIAnalysisCacheProperties cacheProperties,
            AIAnalysisCacheTraceHolder cacheTraceHolder,
            AIAnalysisSingleFlight singleFlight
    ) {
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.cache = cache;
        this.cacheKeyBuilder = cacheKeyBuilder;
        this.cacheProperties = cacheProperties;
        this.cacheTraceHolder = cacheTraceHolder;
        this.singleFlight = singleFlight;
    }

    @Override
    public SearchAnalysis analyze(String query) {
        CacheKey cacheKey = cacheKeyBuilder.build(query);
        cacheTraceHolder.set(AIAnalysisCacheTrace.miss(
                cacheKey.value(),
                cacheProperties.provider(),
                cacheProperties.model(),
                cacheProperties.version()
        ));

        try {
            var cached = cache.get(cacheKey);

            if (cached.isPresent()) {
                cacheTraceHolder.set(AIAnalysisCacheTrace.hit(
                        cacheKey.value(),
                        cacheProperties.provider(),
                        cacheProperties.model(),
                        cacheProperties.version()
                ));
                return cached.get();
            }
        } catch (Exception ignored) {
            // Cache failures must never break AI analysis.
        }

        return singleFlight.execute(cacheKey, () -> {
            SearchAnalysis analysis = analyzeWithAI(query);

            try {
                cache.put(cacheKey, analysis);
            } catch (Exception ignored) {
                // Cache failures must never break AI analysis.
            }

            return analysis;
        });
    }

    private SearchAnalysis analyzeWithAI(String query) {
        try {
            String prompt = promptBuilder.build(query);
            String content = aiService.chat(new ChatRequest(prompt)).content();
            return parser.parse(content, query);
        } catch (Exception ignored) {
            return SearchAnalysis.fallback(query);
        }
    }
}

package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.analysis.cache.AIAnalysisCache;
import io.qwenbridge.analysis.cache.AIAnalysisCacheKeyBuilder;
import io.qwenbridge.analysis.cache.CacheKey;
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

    public QwenSearchAnalysisService(
            AIService aiService,
            SearchAnalysisPromptBuilder promptBuilder,
            SearchAnalysisJsonParser parser,
            AIAnalysisCache cache,
            AIAnalysisCacheKeyBuilder cacheKeyBuilder
    ) {
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
        this.cache = cache;
        this.cacheKeyBuilder = cacheKeyBuilder;
    }

    @Override
    public SearchAnalysis analyze(String query) {
        CacheKey cacheKey = cacheKeyBuilder.build(query);

        try {
            var cached = cache.get(cacheKey);

            if (cached.isPresent()) {
                return cached.get();
            }
        } catch (Exception ignored) {
            // Cache failures must never break AI analysis.
        }

        SearchAnalysis analysis = analyzeWithAI(query);

        try {
            cache.put(cacheKey, analysis);
        } catch (Exception ignored) {
            // Cache failures must never break AI analysis.
        }

        return analysis;
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

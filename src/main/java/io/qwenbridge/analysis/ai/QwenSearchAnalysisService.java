package io.qwenbridge.analysis.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.service.AIService;
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

    public QwenSearchAnalysisService(
            AIService aiService,
            SearchAnalysisPromptBuilder promptBuilder,
            SearchAnalysisJsonParser parser
    ) {
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.parser = parser;
    }

    @Override
    public SearchAnalysis analyze(String query) {
        try {
            String prompt = promptBuilder.build(query);
            String content = aiService.chat(new ChatRequest(prompt)).content();
            return parser.parse(content, query);
        } catch (Exception ignored) {
            return SearchAnalysis.fallback(query);
        }
    }
}

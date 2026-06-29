package io.qwenbridge.semantic;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.semantic.ai.AISemanticService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class DefaultSemanticService implements SemanticService {

    private final AISemanticService aiSemanticService;

    public DefaultSemanticService(AISemanticService aiSemanticService) {
        this.aiSemanticService = aiSemanticService;
    }

    @Override
    public SemanticAnalysis analyze(String query) {
        try {
            return aiSemanticService.analyze(query);
        } catch (Exception exception) {
            log.warn("AI semantic analysis failed. Falling back to basic semantic analysis. query={}", query, exception);
            return SemanticAnalysis.basic(query);
        }
    }
}

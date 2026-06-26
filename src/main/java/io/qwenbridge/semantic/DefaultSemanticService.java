package io.qwenbridge.semantic;

import io.qwenbridge.semantic.ai.AISemanticService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DefaultSemanticService implements SemanticService {

    private static final Logger log = LoggerFactory.getLogger(DefaultSemanticService.class);

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

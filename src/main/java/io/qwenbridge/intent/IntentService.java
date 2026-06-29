package io.qwenbridge.intent;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.intent.ai.AIIntentService;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class IntentService {

    private final AIIntentService aiIntentService;

    public IntentService(AIIntentService aiIntentService) {
        this.aiIntentService = aiIntentService;
    }

    public IntentAnalysis analyze(String query) {
        try {
            return aiIntentService.analyze(query);
        } catch (Exception exception) {
            log.warn("AI intent analysis failed. Falling back to product search intent. query={}", query, exception);
            return IntentAnalysis.productSearch();
        }
    }

    public String detect(String query) {
        return analyze(query).type().name();
    }
}

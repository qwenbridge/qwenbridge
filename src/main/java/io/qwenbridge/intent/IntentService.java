package io.qwenbridge.intent;

import io.qwenbridge.intent.ai.AIIntentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IntentService {

    private static final Logger log = LoggerFactory.getLogger(IntentService.class);

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

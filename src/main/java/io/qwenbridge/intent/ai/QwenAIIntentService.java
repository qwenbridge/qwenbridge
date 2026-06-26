package io.qwenbridge.intent.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.intent.IntentAnalysis;
import org.springframework.stereotype.Service;

@Service
public class QwenAIIntentService implements AIIntentService {

    private final AIService aiService;
    private final IntentPromptBuilder promptBuilder;
    private final IntentJsonParser jsonParser;

    public QwenAIIntentService(
            AIService aiService,
            IntentPromptBuilder promptBuilder,
            IntentJsonParser jsonParser
    ) {
        this.aiService = aiService;
        this.promptBuilder = promptBuilder;
        this.jsonParser = jsonParser;
    }

    @Override
    public IntentAnalysis analyze(String query) {
        String prompt = promptBuilder.build(query);

        ChatResponse response = aiService.chat(new ChatRequest(prompt));

        return jsonParser.parse(response.content());
    }
}

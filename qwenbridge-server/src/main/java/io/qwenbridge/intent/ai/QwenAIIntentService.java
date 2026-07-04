package io.qwenbridge.intent.ai;

import lombok.RequiredArgsConstructor;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.intent.IntentAnalysis;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor

public class QwenAIIntentService implements AIIntentService {

    private final AIService aiService;
    private final IntentPromptBuilder promptBuilder;
    private final IntentJsonParser jsonParser;

    @Override
    public IntentAnalysis analyze(String query) {
        String prompt = promptBuilder.build(query);

        ChatResponse response = aiService.chat(new ChatRequest(prompt));

        return jsonParser.parse(response.content());
    }
}

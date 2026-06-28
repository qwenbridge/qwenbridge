package io.qwenbridge.rewrite.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import org.springframework.stereotype.Service;

@Service

public class QwenAIRewriteService implements AIRewriteService {

    private final AIService aiService;

    public QwenAIRewriteService(AIService aiService) {
        this.aiService = aiService;
    }

    @Override
    public String rewrite(String query) {
        ChatResponse response = aiService.chat(new ChatRequest("""
                Rewrite the following user search query.

                Rules:
                - Fix typos.
                - Keep the same language as the input.
                - Do not explain.
                - Return only the rewritten query.
                - If no rewrite is needed, return the original query.

                Query:
                %s
                """.formatted(query)));

        return response.content() == null ? query : response.content().trim();
    }
}

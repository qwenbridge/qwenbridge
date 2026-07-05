package io.qwenbridge.semantic.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.contract.ChatResponse;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.semantic.SemanticAnalysis;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QwenAISemanticService implements AISemanticService {

  private final AIService aiService;
  private final SemanticPromptBuilder promptBuilder;
  private final SemanticJsonParser jsonParser;

  @Override
  public SemanticAnalysis analyze(String query) {
    String prompt = promptBuilder.build(query);

    ChatResponse response = aiService.chat(new ChatRequest(prompt));

    return jsonParser.parse(response.content());
  }
}

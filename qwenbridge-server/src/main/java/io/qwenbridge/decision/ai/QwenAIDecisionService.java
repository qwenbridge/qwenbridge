package io.qwenbridge.decision.ai;

import io.qwenbridge.ai.contract.ChatRequest;
import io.qwenbridge.ai.service.AIService;
import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.pipeline.ExecutionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class QwenAIDecisionService implements AIDecisionService {

  private final AIService aiService;
  private final DecisionPromptBuilder promptBuilder;
  private final DecisionJsonParser parser;

  @Override
  public SearchDecision decide(ExecutionContext context) {
    String prompt = promptBuilder.build(context);

    String content = aiService.chat(new ChatRequest(prompt)).content();

    return parser.parse(content);
  }
}

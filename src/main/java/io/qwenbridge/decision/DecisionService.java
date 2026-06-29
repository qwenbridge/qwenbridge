package io.qwenbridge.decision;

import lombok.extern.slf4j.Slf4j;
import io.qwenbridge.decision.ai.AIDecisionService;
import io.qwenbridge.pipeline.ExecutionContext;
import org.springframework.stereotype.Service;

@Service

@Slf4j
public class DecisionService {

    private final AIDecisionService aiDecisionService;

    public DecisionService(AIDecisionService aiDecisionService) {
        this.aiDecisionService = aiDecisionService;
    }

    public SearchDecision decide(ExecutionContext context) {
        try {
            return aiDecisionService.decide(context);
        } catch (Exception ex) {
            log.warn("AI decision failed. Falling back to safe keyword search decision. query={}",
                    context.request().originalQuery(), ex);

            return SearchDecision.keyword();
        }
    }
}

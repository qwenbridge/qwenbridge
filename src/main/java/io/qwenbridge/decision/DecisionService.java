package io.qwenbridge.decision;

import io.qwenbridge.decision.ai.AIDecisionService;
import io.qwenbridge.pipeline.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service

public class DecisionService {

    private static final Logger log = LoggerFactory.getLogger(DecisionService.class);

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

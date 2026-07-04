package io.qwenbridge.decision.ai;

import io.qwenbridge.decision.SearchDecision;
import io.qwenbridge.pipeline.ExecutionContext;

public interface AIDecisionService {
    SearchDecision decide(ExecutionContext context);
}

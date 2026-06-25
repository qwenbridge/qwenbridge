package io.qwenbridge.pipeline.result;

import io.qwenbridge.decision.DecisionType;

public record DecisionResult(DecisionType type) {
    public static DecisionResult none() {
        return new DecisionResult(null);
    }
}

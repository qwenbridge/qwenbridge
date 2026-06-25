package io.omnisearch.pipeline.result;

import io.omnisearch.decision.DecisionType;

public record DecisionResult(DecisionType type) {
    public static DecisionResult none() {
        return new DecisionResult(null);
    }
}

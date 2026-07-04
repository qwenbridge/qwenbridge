package io.qwenbridge.ai.contract;

import java.util.List;

public record EmbeddingResponse(
        List<Double> vector
) {
}

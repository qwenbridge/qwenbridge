package io.qwenbridge.pipeline.result;

import java.time.Instant;
import java.util.UUID;

public record RequestContext(
        String requestId,
        Instant startedAt,
        String originalQuery
) {
    public static RequestContext of(String originalQuery) {
        return new RequestContext(
                UUID.randomUUID().toString(),
                Instant.now(),
                originalQuery
        );
    }
}

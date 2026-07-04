package io.qwenbridge.sdk.streaming.payload;

public record AICompletedStreamingPayload(
        String requestId,
        long tokenCount,
        boolean terminal
) implements StreamingPayload {
}

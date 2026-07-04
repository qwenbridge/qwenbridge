package io.qwenbridge.sdk.streaming.payload;

public record AITokenStreamingPayload(
        String requestId,
        long tokenIndex,
        String content,
        boolean terminal
) implements StreamingPayload {
}

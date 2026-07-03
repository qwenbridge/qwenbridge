package io.qwenbridge.streaming.event;

public record AITokenStreamingPayload(
        String requestId,
        long tokenIndex,
        String content,
        boolean terminal
) implements StreamingPayload {
}

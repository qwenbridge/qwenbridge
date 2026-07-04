package io.qwenbridge.streaming.event;

public record ConnectedStreamingPayload(
        String requestId,
        String sessionId
) implements StreamingPayload {
}

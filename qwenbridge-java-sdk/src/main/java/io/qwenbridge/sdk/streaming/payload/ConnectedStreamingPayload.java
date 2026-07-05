package io.qwenbridge.sdk.streaming.payload;

public record ConnectedStreamingPayload(String requestId, String sessionId)
    implements StreamingPayload {}

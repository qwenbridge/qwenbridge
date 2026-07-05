package io.qwenbridge.streaming.event;

public record AICompletedStreamingPayload(String requestId, long tokenCount, boolean terminal)
    implements StreamingPayload {}

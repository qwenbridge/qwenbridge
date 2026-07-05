package io.qwenbridge.streaming.event;

public record AIFailedStreamingPayload(
    String requestId, String code, String message, boolean terminal) implements StreamingPayload {}

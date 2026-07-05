package io.qwenbridge.streaming.event;

public record FailureStreamingPayload(
    String requestId, String code, String message, boolean terminal) implements StreamingPayload {}

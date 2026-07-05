package io.qwenbridge.sdk.streaming.payload;

public record AIFailedStreamingPayload(
    String requestId, String code, String message, boolean terminal) implements StreamingPayload {}

package io.qwenbridge.sdk.exception;

import java.time.Instant;

public record QwenBridgeApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String requestId) {}

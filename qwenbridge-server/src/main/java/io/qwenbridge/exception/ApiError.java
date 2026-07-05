package io.qwenbridge.exception;

import java.time.Instant;
import lombok.Builder;

@Builder
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String requestId) {}

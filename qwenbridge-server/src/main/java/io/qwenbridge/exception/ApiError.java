package io.qwenbridge.exception;

import lombok.Builder;

import java.time.Instant;

@Builder
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        String requestId
) {
}

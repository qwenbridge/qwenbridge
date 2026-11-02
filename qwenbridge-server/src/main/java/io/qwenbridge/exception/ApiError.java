package io.qwenbridge.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import lombok.Builder;

/**
 * The public error body (QB-181). The first seven fields are the v1 contract and never change;
 * {@code category} and {@code retryable} are present on {@code /api/v2} responses only, because
 * released v1 SDKs parse this body strictly.
 */
@Builder
public record ApiError(
    Instant timestamp,
    int status,
    String error,
    String code,
    String message,
    String path,
    String requestId,
    @JsonInclude(JsonInclude.Include.NON_NULL) String category,
    @JsonInclude(JsonInclude.Include.NON_NULL) Boolean retryable) {}

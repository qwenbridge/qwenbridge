package io.qwenbridge.operations.tracing;

public record TraceContext(String traceId, String traceparent) {
}

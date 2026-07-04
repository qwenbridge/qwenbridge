# QwenBridge Tracing Foundation

QwenBridge accepts and propagates W3C `traceparent`. If no valid header is provided, the application creates a new trace context.

Response headers:

- `X-Trace-Id`
- `traceparent`

Structured logs include `traceId` through MDC. This foundation is intentionally vendor-neutral; OpenTelemetry export can be enabled later without changing the public contract.

# Tracing

Tracing connects request handling, pipeline execution, provider calls, and streaming behavior into one observable flow.

## Trace context

A trace context should include:

- request ID
- trace ID where available
- route
- tenant or locale context where applicable
- pipeline stage
- provider name

## Pipeline tracing

Pipeline tracing should make it possible to understand:

- which steps executed
- which steps were skipped
- which step failed
- how long each step took
- what execution plan was produced

## Provider tracing

Provider calls should preserve request correlation when calling:

- Ollama
- OpenSearch
- Redis

## Streaming tracing

Streaming tracing should correlate the original analysis request with the SSE stream request and terminal event.

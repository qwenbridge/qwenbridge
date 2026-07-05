# Metrics

QwenBridge metrics are intended to support runtime visibility, release validation, and production troubleshooting.

## Metric areas

Important metric categories include:

- HTTP request count
- HTTP request latency
- HTTP error count
- provider latency
- provider failure count
- cache hit and miss count
- rate limit decisions
- pipeline stage duration
- SSE session count
- SSE event count
- dependency health state

## HTTP metrics

HTTP metrics should be grouped by:

- method
- route
- status
- outcome

Avoid high-cardinality labels such as raw query text or arbitrary user input.

## Provider metrics

Provider metrics should make it possible to compare:

- Ollama latency
- OpenSearch latency
- Redis latency
- failure frequency
- timeout frequency

## Release verification

Metrics are not a replacement for tests, but they help validate performance and resilience behavior during release verification.

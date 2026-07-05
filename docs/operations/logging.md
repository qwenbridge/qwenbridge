# Logging

QwenBridge logs are designed for debugging API requests, pipeline execution, provider behavior, streaming sessions, and operational failures.

## Request correlation

Every request should have a request ID.

The request ID is used across:

- API responses
- logs
- pipeline events
- SSE streams
- SDK exceptions
- release verification output

## Log format

Production logs should be structured enough to support searching by:

- request ID
- route
- status
- provider
- pipeline stage
- error code
- latency
- dependency name

## Logging rules

Do not log:

- secrets
- tokens
- passwords
- private keys
- full authorization headers
- sensitive user payloads beyond what is required for safe debugging

## Failure logs

Provider and dependency failures should include:

- request ID
- dependency or provider name
- timeout or status code
- safe error summary
- retry outcome where applicable

## Streaming logs

SSE logs should make it possible to trace:

- session creation
- event publication
- terminal event delivery
- disconnects
- failures

# Provider Reliability

QwenBridge treats AI providers and search providers as replaceable external dependencies.

## Reliability principles

- isolate provider-specific DTOs
- apply explicit timeout settings
- convert provider failures into safe application errors
- preserve request ID correlation
- retry only transient failures
- degrade only when the configured policy allows degradation

## AI provider failures

AI provider failures may affect intent analysis, rewrite, semantic analysis, decision making, embeddings, and token streaming. Failures must be reported with safe error messages and correlated request IDs.

## Search provider failures

Search provider failures may affect keyword, vector, hybrid, facet, and rerank execution. Search errors should not leak provider internals through public API contracts.

## Redis failures

Redis may be used for cache and rate limiting. Cache failures can degrade to a safe fallback when configured. Rate-limiting failures in production should be handled according to the selected safety policy.

## SDK behavior

SDK retry behavior must stay aligned with server reliability semantics. SDKs should retry transient failures and avoid retrying validation errors or other non-transient failures.

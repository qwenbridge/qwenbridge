# ADR 0009: Execution Engine and Search Provider Integration

## Status

Accepted

## Context

QwenBridge V3 introduced a backend-neutral Search Provider SPI.

The execution engine previously executed only operation-specific executors and returned string-based execution results. This was useful for the MVP pipeline, but it did not connect execution plans to real search providers.

To move toward real provider-based search execution, the execution engine needs to resolve a search provider, build a provider request, execute it, and return the result through the existing execution result contract without breaking V2 behavior.

## Decision

QwenBridge extends the `ExecutionEngine` API with an overloaded method:

~~~java
ExecutionResult execute(ExecutionPlan plan, ExecutionContext context);
~~~

The existing method remains unchanged:

~~~java
ExecutionResult execute(ExecutionPlan plan);
~~~

This preserves V2 compatibility while allowing V3 to pass full pipeline context into the execution layer.

A new `SearchRequestFactory` creates a `SearchRequest` from `ExecutionContext`.

The factory prefers a rewritten query when available and falls back to the original request query.

`DefaultExecutionEngine` now resolves a `SearchProvider` using `SearchProviderResolver`, executes the provider, and maps provider hits back into the existing `ExecutionResult` string result list.

## Consequences

Positive:

- V2 execution remains backward compatible.
- V3 can execute through the provider layer.
- Search provider selection is centralized through `SearchProviderResolver`.
- Search request construction is isolated in `SearchRequestFactory`.
- The execution engine no longer needs to know concrete provider implementations.

Trade-offs:

- `ExecutionResult` still exposes `List<String>` results.
- Rich provider responses are temporarily flattened into string output.
- A future version should introduce a richer execution result model for structured search results.

## Decision Summary

The execution engine is now connected to the Search Provider SPI without breaking the existing execution contract.

This creates the first end-to-end bridge from pipeline context to pluggable search providers.

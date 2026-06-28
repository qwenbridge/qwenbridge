# ADR-0010: V3 OpenSearch Provider Integration

## Status
Accepted

## Context
QwenBridge V3 introduces a real search backend integration through the Search Provider SPI.

## Decision
OpenSearch is integrated as a concrete SearchProvider implementation. The execution flow is:

SearchDecision
→ ExecutionPlan
→ ExecutionEngine
→ SearchProviderResolver
→ OpenSearchProvider
→ OpenSearchClient
→ OpenSearchResponseMapper
→ SearchResponse

## Consequences
- Search execution is no longer mock-only.
- The API can return real indexed results.
- OpenSearch is selected through backend-aware provider resolution.
- V3 is functionally complete.
- Current latency is dominated by sequential AI calls, not OpenSearch.

## Notes
Performance optimization is deferred to V4 through parallel AI execution, prompt fusion, and caching.

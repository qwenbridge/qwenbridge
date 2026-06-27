# ADR 0008: Search Provider SPI

## Status

Accepted

## Context

QwenBridge V3 introduces a Search Provider Layer.

Before V3, execution was based on operation-specific executors. This was enough for the early MVP, but it did not provide a stable provider abstraction for real search backends such as OpenSearch, Elasticsearch, Meilisearch, Typesense, SQL, or vector databases.

QwenBridge needs a provider SPI that allows search backends to be added without changing the execution pipeline.

## Decision

QwenBridge introduces a Search Provider SPI under:

```text
io.qwenbridge.execution.provider
```

The package structure is:

```text
execution/provider
├── implementation
├── model
├── registry
├── resolver
├── spi
└── support
```

The main provider contract is:

```java
SearchProvider
```

with:

```java
String name();
SearchResponse search(SearchRequest request);
```

Provider discovery is handled through:

```java
SearchProviderRegistry
```

Provider selection is handled through:

```java
SearchProviderResolver
```

A default Spring-based registry automatically registers all `SearchProvider` beans.

The default resolver selects the provider from execution hints when available, otherwise it falls back to `inmemory`.

## Search Model

The provider model is intentionally backend-neutral.

### SearchRequest

Contains:

- query
- constraints
- options

### SearchResponse

Contains:

- result set

### SearchResultSet

Contains:

- hits
- total hits
- took millis

### SearchHit

Contains:

- id
- score
- document
- metadata

`SearchHit` intentionally does not contain fixed fields such as `title`, `url`, or `snippet`.

Different backends return different document shapes. A generic document map keeps the provider model flexible and avoids forcing all backends into a UI-specific representation.

## Consequences

Positive:

- Search backends become pluggable.
- Execution can remain backend-agnostic.
- Provider implementations can be tested independently.
- Spring automatically discovers provider beans.
- InMemory provider acts as a reference implementation.
- Future providers such as OpenSearch and Elasticsearch can be added without changing core contracts.

Trade-offs:

- The first version of the SPI is intentionally minimal.
- Advanced features such as facets, aggregations, ranking signals, and hybrid search metadata will be added incrementally.
- Mapping from execution plans to search requests remains a later integration concern.

## Decision Summary

QwenBridge V3 introduces a backend-neutral Search Provider SPI as the foundation for real search execution.

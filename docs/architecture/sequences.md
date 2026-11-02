# Sequence Diagrams

These seven interactions are taken from the current source (QB-287, 2026-11-02). The
[dual-SDK E2E suite](../../e2e/dual-sdk/README.md) exercises all of them against a real server.

## 1. v2 search with the Java SDK

```mermaid
sequenceDiagram
    autonumber
    participant App as Java application
    participant SDK as CommerceClient (Java SDK)
    participant API as CommerceResource /api/v2
    participant Sec as ApiIdentity · Authorization · rate limits
    participant Svc as CommerceSearchService
    participant Plan as CommercePlanning
    participant BE as CommerceBackends → MemoryCommerceBackend
    App->>SDK: requireCompatibleServer()
    SDK->>API: GET /api/v2/version
    API-->>SDK: version, contracts
    App->>SDK: search(Request.planned("black shoes under 100 EUR"))
    SDK->>API: POST /api/v2/search<br/>Authorization, X-Request-Id, X-Request-Timeout-Ms
    API->>Sec: resolve principal, check role, consume limits
    Sec-->>API: tenant, subject
    API->>Svc: search(request, principal)
    Svc->>Svc: language · typo · threats · model analysis · expansion · QueryIR · validation
    Svc->>Plan: execute(validated plan)
    Plan->>BE: search(query) keyword and vector
    BE-->>Plan: hits
    Plan-->>Svc: fused, verified, reranked results
    Svc-->>API: SearchResponse with timings
    API-->>SDK: 200 JSON
    SDK-->>App: CommerceContracts.Response
```

On a retryable failure the SDK repeats the call with the same request ID. A failure is retryable
when it is a transport error, or when the v2 error says `retryable` (v1: 429, 502, 503 or 504). It never retries session turns, acceptances or streams.

## 2. v2 SSE stream with the TypeScript SDK

```mermaid
sequenceDiagram
    autonumber
    participant App as Node.js or browser
    participant SDK as CommerceClient (TypeScript SDK)
    participant API as CommerceResource /api/v2/search/stream
    participant Svc as CommerceSearchService
    App->>SDK: stream(request, handler, signal)
    SDK->>API: POST /api/v2/search/stream (Accept: text/event-stream)
    API-->>SDK: event search.started (sequence 1)
    loop while the search runs
      API-->>SDK: :heartbeat comment
    end
    API->>Svc: search(request, principal)
    Svc-->>API: SearchResponse
    API-->>SDK: event search.completed (terminal, sequence 2, traceId)
    SDK-->>App: handler(event) for each event
    Note over SDK,API: On failure the only terminal event is search.failed with a typed error
```

Exactly one terminal event is sent, and sequence numbers increase strictly.

## 3. Cancellation

```mermaid
sequenceDiagram
    autonumber
    participant App
    participant SDK as SDK client
    participant API as /api/v2
    participant Reg as ApiRequests registry
    participant Svc as CommerceSearchService
    App->>SDK: searchAsync(request with id r-1)
    SDK->>API: POST /api/v2/search (X-Request-Id r-1)
    API->>Reg: register r-1 with a cancellation token
    API->>Svc: search
    App->>SDK: cancel(true) or AbortSignal.abort()
    SDK->>SDK: abort the HTTP call
    SDK->>API: DELETE /api/v2/requests/r-1
    API->>Reg: cancel r-1
    Reg-->>Svc: token cancelled
    Svc-->>API: stops at the next stage boundary
    API-->>SDK: 200 {requestId, cancelled: true}, or 404 if unknown, finished or another caller's
    Note over Svc: A stream ends with search.failed, code CANCELLED, after the in-flight model call returns
```

## 4. Typo correction and multilingual expansion inside a search

```mermaid
sequenceDiagram
    autonumber
    participant Svc as CommerceSearchService
    participant Lang as LanguageService
    participant Typo as TypoService
    participant TC as TypoCorrector (core)
    participant Exp as ExpansionService
    participant QE as QueryExpander (core)
    participant TP as Glossary or Model TranslationProvider
    Svc->>Lang: analyze(query)
    Lang-->>Svc: LanguageAnalysis
    Svc->>Typo: correct(tenant, query, searchForm, analysis)
    Typo->>TC: correct with tenant TypoSettings
    TC-->>Typo: CorrectionResult (corrections, protected tokens)
    Typo-->>Svc: corrected or suggested text
    Note over Svc: deterministic threats, model analysis, model safety
    Svc->>Exp: expand(tenant, source text, analysis, deadline)
    Exp->>QE: expand with tenant ExpansionSettings
    loop each configured target language
      QE->>TP: translate(source → target)
      TP-->>QE: text, confidence, preservation report
    end
    QE-->>Exp: ExpansionResult (per-target status)
    Exp-->>Svc: retrievalTexts
    Svc->>Svc: retrieval over the original and each SUCCESS target, then rank fusion
```

## 5. Catalog synchronization

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Operator (catalog:write)
    participant API as POST /api/v2/catalog/sync
    participant Sync as CatalogSynchronization
    participant R as Redis
    participant Feed as http-json catalog feed
    participant Pack as Commerce domain pack
    participant BE as Selected CommerceBackend
    Admin->>API: sync
    API->>Sync: synchronize(principal)
    Sync->>R: SET lock NX EX 30m
    alt Redis unreachable
      Sync-->>API: 503 DEPENDENCY_UNAVAILABLE
    else lock held by another instance
      Sync-->>API: 409 CONFLICT
    else acquired
      Sync->>R: GET checkpoint cursor
      loop each page
        Sync->>Feed: GET page(cursor) with bearer token
        Feed-->>Sync: changes, nextCursor
        Sync->>Pack: normalize, expand variants
        Sync->>BE: upsert(tenant, variantId, document)
        Sync->>R: SET cursor, renew lock, record seen ids
      end
      Sync->>BE: delete ids not seen (complete snapshot sources)
      Sync->>R: release lock
      Sync-->>API: counts
    end
```

## 6. Pack installation and activation

```mermaid
sequenceDiagram
    autonumber
    participant Admin as Operator
    participant API as PlatformResource /api/v2/platform
    participant MP as ManagedPacks
    participant SD as SignedDocuments
    participant Ent as Entitlements
    participant Store as PlatformStore (memory or file)
    participant Pack as QwenBridgePack
    Admin->>API: POST /packs (signed manifest, jar, signature)
    API->>MP: install(principal, packet)
    MP->>SD: verify signature with a trusted key
    MP->>MP: check apiVersion, runtime range, dependencies
    MP->>Store: save version
    API-->>Admin: View (inactive)
    Admin->>API: POST /packs/{id}/activate
    API->>MP: activate(principal, id, configuration)
    MP->>Ent: require edition feature
    MP->>Pack: start(Context tenant, configuration, secrets)
    alt start fails
      MP->>Store: record reason, stay inactive
    else started
      MP->>Store: mark active for the tenant
    end
    API-->>Admin: state
```

## 7. Legacy v1 analyze and stream

```mermaid
sequenceDiagram
    autonumber
    participant App as v1 client (QwenBridgeClient)
    participant API as /api/v1/search
    participant SP as SearchPipeline
    participant PE as PipelineEngine (15 ordered steps)
    participant Bus as Pipeline event publisher
    participant SSE as Streaming registry
    App->>API: GET /api/v1/search/stream/{requestId}
    API->>SSE: register session
    SSE-->>App: event stream.connected
    App->>API: POST /api/v1/search/analyze {requestId, query}
    API->>SP: analyze
    SP->>PE: run steps (language → … → confidence)
    loop each step
      PE->>Bus: step started and completed
      Bus->>SSE: publish for requestId
      SSE-->>App: typed pipeline event
    end
    PE-->>SP: context
    SP-->>API: SearchAnalyzeResponse
    API-->>App: 200 JSON
    SSE-->>App: completion event (terminal)
```

The legacy v1 API is refused (409) in deployments that require authentication, because it is not
tenant-aware. [Remediation proposal 2](remediation-proposals.md#2-serve-the-v1-api-from-the-core-engine)
describes how to serve it from the Core engine.

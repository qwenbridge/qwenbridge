# Flows

These 15 flowcharts show how the main operations proceed in the current source (QB-287,
2026-11-02). Each one names the class that implements it. The [sequence diagrams](sequences.md)
show the same paths as interactions between components.

## 1. v2 search, end to end

```mermaid
flowchart TD
    A[POST /api/v2/search] --> B[Authenticate API key<br/>resolve tenant and roles]
    B --> C[Rate limits and quotas]
    C --> D[Request validation<br/>size, request id, deadline header]
    D --> E[Language analysis]
    E --> F[Typo correction<br/>per tenant]
    F --> G[Deterministic threat analysis]
    G -->|blocked| X[Typed refusal]
    G --> H[Model analysis]
    H --> I[Model safety check]
    I -->|blocked| X
    I --> J[Multilingual expansion<br/>per tenant, after both safety checks]
    J --> K[Grounded extraction<br/>QueryIR]
    K --> L[Schema validation<br/>CommerceCompiler.validate]
    L -->|invalid hard constraint| Q[Clarification questions]
    L --> M[Retrieval planning<br/>CommercePlanning]
    M --> N[Backend search<br/>memory or opensearch]
    N --> O[Result verification<br/>against hard constraints]
    O --> P[Reranking · recovery · suggestions]
    P --> R[Evidence, metrics, analytics]
    R --> S[200 response with timings]
```

Implemented in `CommerceSearchService.search` and `run`. Each stage writes its duration into
`timings`. Under the planned contract (`commerce-search/1.0`), `CommerceInterpretation` replaces
the direct compile step and the planner chooses the strategy.

## 2. Language detection

```mermaid
flowchart TD
    A[Input text] --> B{Declared language?}
    B -->|yes, valid tag| C[Keep the declared tag]
    B -->|no| D[Split into script segments]
    D --> E[Model detector on the whole text]
    D --> F[ScriptHeuristicLanguageDetector<br/>per segment]
    E --> G{Confident?}
    G -->|yes| H[Primary language]
    G -->|no| F
    F --> H
    C --> I[LanguageAnalysis]
    H --> I
    I --> J[mixed flag · segments · text direction<br/>configuredLanguage]
```

Implemented in `language.LanguageService`. Hindi in Devanagari, and Persian or Urdu by letter
sets, are separated by script rules.

## 3. Text normalization

```mermaid
flowchart LR
    A[Raw query] --> B[disallowed-controls<br/>C0/C1, bidi controls, BOM, soft hyphen]
    B --> C[unicode-nfc<br/>canonical composition only]
    C --> D[persian-variants<br/>Persian only]
    D --> E[zero-width-non-joiner<br/>collapse, trim]
    E --> F[whitespace<br/>one ASCII space]
    F --> G[Search form]
    A --> H[Threat canonicalization<br/>separate form, never searched]
```

Implemented in `core.text.SearchTextNormalizer` (version `search-normalization-v1`). Each step is
deterministic, and the pipeline is idempotent. NFKC, case folding, URL or HTML decoding and digit
conversion are deliberately not applied, because they would change identifiers. The threat form
keeps the evidence the detectors need. The search form feeds typo correction and retrieval.

## 4. Typo correction

```mermaid
flowchart TD
    A[Search form, tenant] --> B{Typo enabled for tenant?}
    B -->|no| Z1[DISABLED]
    B -->|yes| C{Provider installed?}
    C -->|no| Z2[NO_PROVIDER]
    C -->|yes| D{Detected language enabled?}
    D -->|no, and no enabled language knows more words| Z3[LANGUAGE_NOT_ENABLED]
    D -->|yes, or overruled by vocabulary evidence| E[Tokenize]
    E --> F[Protect URLs, e-mail, hosts,<br/>amounts, identifiers]
    F --> G{Word known in any enabled language?}
    G -->|yes| K[Keep]
    G -->|no| H[Candidates within edit distance<br/>keyboard-aware]
    H --> I{Confidence over mode threshold?}
    I -->|SUGGEST_ONLY| S[Suggest only]
    I -->|SAFE_AUTO_CORRECT, safe threshold| T[Correct]
    I -->|AGGRESSIVE, lower threshold| T
    I -->|below| K
    K & S & T --> R[CorrectionResult]
```

Implemented in `core.spelling.TypoCorrector`; tenant settings come from `spelling.TypoConfiguration`.

## 5. Multilingual expansion

```mermaid
flowchart TD
    A[Normalized, possibly corrected query] --> B{Expansion enabled for tenant?}
    B -->|no| Z[Status DISABLED]
    B -->|yes| C[Resolve target languages<br/>aliases, regions, de-duplication]
    C --> D[Resolve source language<br/>vocabulary evidence first]
    D --> E[For each target]
    E --> F{Same as source?}
    F -->|yes| U[UNCHANGED]
    F -->|no| G{Provider supports the pair?}
    G -->|no| V[UNSUPPORTED_LANGUAGE_PAIR or PROVIDER_UNAVAILABLE]
    G -->|yes| H[Translate within the deadline]
    H --> I{Identifiers preserved, confidence high enough?}
    I -->|yes| S[SUCCESS: added to retrievalTexts]
    I -->|no| L[LOW_CONFIDENCE: reported, not searched]
    U & V & S & L --> R[ExpansionResult per target]
```

Implemented in `core.expansion.QueryExpander` and `multilingual.ExpansionService`, which applies a
semaphore and the overall deadline. A target without a translation never carries invented text.

## 6. Grounded extraction and QueryIR validation

```mermaid
flowchart TD
    A[Model analysis and deterministic rules] --> B[Candidate constraints]
    B --> C{Value quoted in the original text?}
    C -->|no| D[Dropped, reason recorded]
    C -->|yes| E[Constraint with Span and USER_TEXT provenance]
    E --> F[QueryIR 1.1]
    F --> G[QueryIrValidator]
    G --> H{Field in schema, operator allowed,<br/>value type correct?}
    H -->|no| I[IrViolation, then clarification]
    H -->|yes| J[Validated plan]
```

## 7. Threat and policy gate

```mermaid
flowchart TD
    A[Threat form] --> B[12 detector families<br/>SQL · NoSQL · LDAP · command · template · traversal<br/>SSRF · XSS · prompt · jailbreak · secret · Unicode]
    B --> C[Score and correlate]
    C --> D{Risk level}
    D -->|block| X[Refused, typed error, explanation]
    D -->|review| Y[Approval required<br/>policy approvals]
    D -->|allow| E[Policy packs, in evaluation order]
    E -->|deny| X
    E -->|allow| F[Continue]
```

## 8. Retrieval planning

```mermaid
flowchart TD
    A[Validated plan, backend capabilities] --> B{Requested mode?}
    B -->|explicit| C[Use it, if supported<br/>else typed 422]
    B -->|planned contract| D[CommercePlanning chooses]
    D --> E{Embeddings available<br/>and within latency budget?}
    E -->|no| K[KEYWORD]
    E -->|yes| F{Keyword signal useful?}
    F -->|yes| H[HYBRID]
    F -->|no| V[VECTOR_ONLY]
    K & H & V --> G[Execute]
    G --> I{Degradation?}
    I -->|yes| J[Report degradations and fallback in retrieval]
    I -->|no| R[Results]
    J --> R
```

## 9. Hybrid fusion

```mermaid
flowchart LR
    Q[Query] --> K[Keyword search]
    Q --> V[Embed, then vector search]
    E[Expansion texts] --> K2[Keyword search per language]
    K --> F[RankFusion<br/>reciprocal rank]
    V --> F
    K2 --> F
    F --> T[Top-K fused documents<br/>with source ranks]
```

## 10. Result verification and zero-result recovery

```mermaid
flowchart TD
    A[Backend hits] --> B[Check every hit against HARD constraints]
    B --> C[Remove violating hits<br/>count removedByValidation]
    C --> D{Any hits left?}
    D -->|yes| E[Rank and rerank]
    D -->|no| F[ZeroResultRecovery]
    F --> G[Propose relaxations<br/>soft first, never silent]
    G --> H[Response with proposals<br/>client accepts explicitly]
```

## 11. Backend selection

```mermaid
flowchart TD
    A[Request backend id, or none] --> B[id = requested or qwenbridge.commerce.backend]
    B --> C{Registered?<br/>memory, opensearch}
    C -->|no| X[Unknown backend<br/>elasticsearch, postgresql, mongodb since QB-287]
    C -->|yes| D[Entitlements.require tenant, search]
    D -->|not entitled| Y[403 edition feature]
    D -->|ok| E[CommerceBackend]
```

Implemented in `connectors.CommerceBackends.backend`.

## 12. Identity, authorization and rate limiting

```mermaid
flowchart TD
    A[Request] --> B{Authorization header?}
    B -->|yes| C[Match an API principal<br/>digest comparison]
    C -->|no match| X[401]
    C --> D[Subject, tenant, roles]
    B -->|no| E{authentication-required?}
    E -->|yes, always in prod| X
    E -->|no| F[Anonymous, search role only]
    D & F --> G{Role allows the operation?}
    G -->|no| Y[403]
    G -->|yes| H[Rate limit: per IP, per key, AI quota, token quota]
    H --> I{Redis rate limiter configured and reachable?}
    I -->|yes| J[Redis fixed window]
    I -->|no| K[In-memory fixed window fallback]
    J & K --> L{Within limit?}
    L -->|no| Z[429 RATE_LIMITED]
    L -->|yes| OK[Handle request]
```

## 13. Catalog synchronization

```mermaid
flowchart TD
    A[POST /api/v2/catalog/sync<br/>catalog:write] --> B{Source configured?}
    B -->|no| X1[Refused: catalog connector is not configured]
    B -->|yes| C{Redis reachable?}
    C -->|no| X2[503 DEPENDENCY<br/>needs Redis for checkpoint and lock]
    C -->|yes| D[SET NX lock per tenant and backend]
    D -->|held elsewhere| X3[409 conflict]
    D --> E[Read checkpoint cursor]
    E --> F[Fetch page from the http-json feed<br/>3 attempts on server errors and rate limits]
    F --> G[Normalize with the domain pack<br/>expand variants]
    G --> H[Upsert into the selected backend]
    H --> I[Store cursor, renew lock]
    I --> J{More pages?}
    J -->|yes| F
    J -->|no| K[Delete records not seen in this scan]
    K --> L[Release lock, return counts]
```

Implemented in `commerce.CatalogSynchronization.synchronize`. A Redis-free design is in
[remediation proposal 3](remediation-proposals.md#3-catalog-synchronization-without-redis).

## 14. Pack lifecycle

```mermaid
flowchart TD
    A[Signed pack: manifest, jar, signature] --> B[inspect: trusted key, signature, digest]
    B -->|invalid| X[Refused]
    B --> C[Compatibility: apiVersion, runtime range, dependencies]
    C -->|incompatible| X
    C --> D[install: store version in PlatformStore]
    D --> E[activate: entitlement, configuration, secrets]
    E -->|start fails| F[Stays inactive, reason recorded]
    E --> G[Active for tenant]
    G --> H[disable]
    G --> I[rollback to previous version]
    I --> G
```

Implemented in `platform.ManagedPacks`.

## 15. Health and readiness

```mermaid
flowchart TD
    A[GET /api/v1/health/ready] --> B[For each DependencyHealthChecker]
    B --> C{active?}
    C -->|no| S[Skipped: not probed, not listed]
    C -->|yes| D[Probe]
    D --> E{Result}
    E -->|reachable| UP[UP]
    E -->|unreachable| DG[DEGRADED, with reason]
    UP & DG & S --> AGG[Aggregate: any DOWN gives DOWN, else any DEGRADED gives DEGRADED, else UP]
    AGG --> H{DOWN?}
    H -->|yes| R503[HTTP 503]
    H -->|no| R200[HTTP 200]
```

Implemented in `operations.health.OperationalHealthService` and `api.health.ApiHealthController`.
The checkers for Ollama, Redis and OpenSearch report an unreachable dependency as `DEGRADED`, so it
never fails readiness. `OpenSearchDependencyHealthChecker.active()` is true only when
`qwenbridge.search.backend` or `qwenbridge.commerce.backend` is `opensearch` (QB-287). Quarkus
`/q/health/live` and `/q/health/ready` (SmallRye Health) report the application itself.

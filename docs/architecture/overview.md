# Architecture Overview

QwenBridge is a multilingual query-understanding and search engine. A framework-free **Core**
holds the language, spelling, expansion, QueryIR, safety and retrieval logic. A **Quarkus server**
exposes it over REST and SSE, and two **SDKs** consume that API. Domain knowledge lives in
**packs**. No database, search engine or cache is mandatory.

This page has the eight structural diagrams. The [class diagrams](class-diagrams.md),
[flows](flows.md) and [sequence diagrams](sequences.md) go into detail.
[Remediation proposals](remediation-proposals.md) describes the couplings that remain and how to
remove them.

**State after QB-287 (2026-11-02):**
- **Reactor:** seven Maven modules plus the TypeScript SDK.
- **Removed:** the Elasticsearch, PostgreSQL, MongoDB and Shopify connector modules, and
  `examples/`.
- **Backends:** the in-process backend (default) and OpenSearch (optional).

## 1. Overall system

```mermaid
flowchart LR
    subgraph Clients
      REST[HTTP clients]
      JS[Java SDK]
      TS[TypeScript SDK]
      WC[Web Component]
    end
    subgraph Server[qwenbridge-server · Quarkus · Java 25]
      API[REST and SSE resources<br/>v2 · platform · legacy v1]
      SEC[Security<br/>API keys · tenants · roles · rate limits]
      PIPE[Query pipeline<br/>v2 commerce service · legacy v1 step pipeline]
      EXE[Planning and execution]
      OPS[Operations<br/>health · metrics · tracing]
      PLAT[Platform<br/>packs · licences · editions · plugins]
    end
    CORE[qwenbridge-core<br/>framework-free engine]
    MEM[(In-process index<br/>default backend)]
    OS[(OpenSearch<br/>optional)]
    RED[(Redis<br/>optional)]
    MOD[Model server<br/>Ollama · HTTP JSON gateway]
    PACKS[Domain packs<br/>Commerce · Library]

    REST & JS & TS & WC --> API
    API --> SEC --> PIPE --> EXE
    PIPE --> CORE
    EXE --> CORE
    EXE --> MEM
    EXE -. selected explicitly .-> OS
    PIPE --> MOD
    Server -. cache, rate limits, catalog-sync lock .-> RED
    PLAT --> PACKS
```

| Concern | Where | Default |
|---|---|---|
| Retrieval | `connectors.MemoryCommerceBackend` (core `InMemoryKeywordIndex`, vector index) | In process |
| Optional retrieval | `connectors.OpenSearchBackend`, `execution.provider.opensearch` | Off |
| Platform state (packs, licences, sessions) | `platform.PlatformStore` | Memory, or a file directory |
| Cache and distributed rate limits | `analysis.cache`, `abuse.RedisBackedRateLimiter` | In-memory fallback |
| Model | `ai.provider.*` | Ollama on `localhost:11434` |

## 2. Maven module graph

```mermaid
flowchart BT
    core[qwenbridge-core<br/>Jackson only]
    sdk[qwenbridge-plugin-sdk<br/>JDK only]
    cpack[qwenbridge-commerce-pack]
    lpack[qwenbridge-library-pack]
    server[qwenbridge-server<br/>Quarkus]
    jsdk[qwenbridge-java-sdk<br/>Jackson only]
    tssdk[qwenbridge-typescript-sdk<br/>npm, no runtime deps]
    e2e[e2e/dual-sdk<br/>standalone consumers]

    cpack --> sdk
    lpack --> sdk
    server --> core
    server --> sdk
    server --> cpack
    server --> lpack
    e2e -->|Maven artifact| jsdk
    e2e -->|npm tarball| tssdk
    e2e -. HTTP .-> server
```

`jsdk` and `tssdk` depend on no other module; they talk to the server only over HTTP. Tests
enforce these rules:
- `ArchitectureNeutralityTest`: the core's only runtime dependency is Jackson; no framework,
  driver, SQL or network-client imports in the core; no Shopify code anywhere.
- `DriverDependencyTest`, `QuarkusOnlyArchitectureTest`, `ShopifyIsolationTest`.
- `scripts/verification/quarkus_only_audit.py`.

## 3. Core components

```mermaid
flowchart TB
    subgraph Text[Text and language]
      text[text<br/>SearchTextNormalizer]
      lang[language<br/>ScriptHeuristicLanguageDetector]
      req[request<br/>LanguageTag · limits]
    end
    subgraph Rewrite[Correction and expansion]
      spell[spelling<br/>TypoCorrector · Lexicon · ProtectedTokens]
      exp[expansion<br/>QueryExpander · TargetLanguages]
      tr[translation<br/>TranslationStage · GlossaryTranslationProvider]
    end
    subgraph Understanding
      intent[intent]
      extr[extraction<br/>grounded spans]
      qir[queryir<br/>QueryIR 1.1 · Constraint · Intent]
      schema[schema<br/>registry]
      valid[validation]
      conf[confidence · evidence · explain]
    end
    subgraph Safety
      threat[threat]
      policy[policy]
      sec[security]
    end
    subgraph Retrieval
      ret[retrieval<br/>SearchProvider · Filter · keyword · vector · hybrid RankFusion]
      plan[planning]
      rank[ranking]
      rec[recovery]
      sug[suggestion]
      conv[conversation · context]
    end
    conn[connector<br/>Connector · ConnectorRuntime · ConnectorPacks SPI]

    text --> lang --> spell --> exp --> tr
    spell --> extr --> qir --> valid
    intent --> qir
    schema --> valid
    qir --> plan --> ret --> rank
    ret --> rec
    threat --> policy
    conn --> ret
```

The core has 234 classes in 28 packages and depends only on `jackson-databind`. Every external
effect (model calls, translation, search engines) enters through an interface the server
implements: `SearchProvider`, `TranslationProvider`, `CorrectionProvider`, `Connector`.

## 4. Server components

```mermaid
flowchart TB
    subgraph API layer
      v2[commerce.CommerceResource<br/>/api/v2]
      conv[commerce.ConversationResource]
      plat[platform.PlatformResource<br/>/api/v2/platform]
      dom[platform.DomainResource]
      ext[extensions.ExtensionsResource · PacksResource]
      v1[api.* · /api/v1 legacy]
      lng[spelling · multilingual resources]
    end
    subgraph Services
      css[commerce.CommerceSearchService]
      cp[commerce.CommercePlanning · CommerceCompiler]
      ci[commerce.CommerceInterpretation]
      crr[commerce.CommerceReranker · ZeroResultRecovery · CommerceSuggestions]
      sync[commerce.CatalogSynchronization]
      pipe[pipeline.SearchPipeline<br/>15 ordered steps]
      typo[spelling.TypoService]
      expn[multilingual.ExpansionService]
      langs[language.LanguageService]
      thr[threat.*]
    end
    subgraph Backends
      cb[connectors.CommerceBackends]
      mem[MemoryCommerceBackend]
      osb[OpenSearchBackend]
      exe[execution.*<br/>legacy v1 executor]
    end
    subgraph Platform
      mp[platform.ManagedPacks]
      ent[platform.Entitlements]
      ps[platform.PlatformStore]
      ph[extensions.PluginHost · ExtensionRegistry]
      cps[extensions.ConnectorPackService]
    end
    ai[ai.provider.*<br/>Ollama · HTTP JSON]
    ops[operations.*<br/>health · metrics · production validator]

    v2 --> css --> cp --> cb
    css --> typo & expn & langs & thr & ai
    css --> ci & crr
    cb --> mem & osb
    v1 --> pipe --> exe --> osb
    pipe --> ai
    plat --> mp --> ent --> ps
    ext --> ph & cps
    sync --> cb
```

The server has 406 classes. The two largest areas are the legacy v1 path (`pipeline` 40,
`execution` 36, `threat` 45, `analysis` 19) and the v2 commerce path (`commerce` 23,
`connectors` 8). [Remediation proposals](remediation-proposals.md) covers both.

## 5. Plugin, pack and adapter SPI

```mermaid
flowchart LR
    subgraph plugin-sdk[qwenbridge-plugin-sdk · JDK only]
      QP[QwenBridgePack]
      DP[DomainPack]
      CP[ConnectorPack]
      PP[PolicyPack]
      PL[QwenBridgePlugin · Plugin]
      AG[Agent · ToolHandler]
    end
    subgraph core-spi[qwenbridge-core SPI]
      CPP[ConnectorPackProvider]
      CON[Connector]
      SP[SearchProvider]
      TP[TranslationProvider]
      CR[CorrectionProvider]
    end
    QP --> DP & CP & PP
    commerce[qwenbridge-commerce-pack] -->|implements| DP
    library[qwenbridge-library-pack] -->|implements| DP
    ext[External JAR<br/>plugins directory] -->|ServiceLoader| PL
    ext -->|ServiceLoader| CPP
    server[qwenbridge-server] -->|implements| SP & TP & CR
    server -->|loads, sandboxes| PL
    server -->|ConnectorPackService| CPP --> CON
```

- **Domain packs:** add schemas, vocabulary and rules. Commerce and Library ship in the reactor.
- **Connector packs:** add data sources through `ConnectorPackProvider`, discovered by
  `ServiceLoader`. Each connector runs inside `ConnectorRuntime`, which provides a circuit
  breaker, retries, timeouts and a typed state.
- **Plugins:** external JARs in a plugins directory, loaded by `PluginHost` in their own class
  loader. They register tools and agents in `ExtensionRegistry`.
- **Gating:** edition features gate packs (`Entitlements`). Distribution is signed
  (`SignedDocuments`).

## 6. SDK integration

```mermaid
flowchart LR
    subgraph Java[Java application]
      JC[CommerceClient<br/>search · searchAsync · stream · refine · sessions · cancel]
      JL[QwenBridgeClient<br/>legacy v1 analyze · typed v1 stream]
    end
    subgraph TS[Node.js or browser]
      TC[CommerceClient<br/>search · stream · sessions · AbortSignal]
      TL[QwenBridgeClient · QwenBridgeStreamingClient<br/>legacy v1]
    end
    S[QwenBridge server]
    JC -->|POST /api/v2/search<br/>POST /api/v2/search/stream SSE| S
    TC -->|same contract| S
    JL -->|/api/v1| S
    TL -->|/api/v1| S
    JC -->|GET /api/v2/version<br/>requireCompatibleServer| S
```

Both SDKs implement the same behaviour:
- **Retries:** only search, refine and reads are retried, always with the same request ID.
- **Deadlines:** the per-call timeout is sent as `X-Request-Timeout-Ms`.
- **Cancellation:** aborts the HTTP call and asks the server to cancel (`DELETE` of the request).
- **Errors:** typed, carrying `status`, `code`, `category` and `retryable`.

The [dual-SDK E2E suite](../../e2e/dual-sdk/README.md) checks that the two SDKs give equivalent
contract views.

## 7. Offline deployment

```mermaid
flowchart TB
    subgraph Host[Single host or container, no internet]
      APP[quarkus-run.jar<br/>Java 25]
      VOL[(Local volume<br/>platform state, signed packs, licence)]
      DATA[(Catalog files<br/>qwenbridge.demo.dataset-files or catalog sync)]
      DICT[(Dictionaries and glossaries<br/>typo · expansion)]
      OLL[Ollama<br/>qwen2.5 · bge-m3]
    end
    APP --> VOL
    APP --> DATA
    APP --> DICT
    APP -->|localhost| OLL
    APP -. optional .-> RED[(Redis)]
    APP -. optional .-> OSX[(OpenSearch)]
```

**Without Redis and OpenSearch:**
- the server starts and reports ready;
- retrieval runs in process;
- licences are verified offline against trusted keys.

**What needs the optional services:**
- **Catalog synchronization from an HTTP feed** needs Redis for its checkpoint and lock. Without
  it, the endpoint returns a typed 503.
- **Model-backed stages** need the local model server.

Docker Compose matches this layout. The default profile starts Ollama and Redis.
`--profile production` adds the application; `--profile opensearch` adds OpenSearch.

## 8. Future extension

```mermaid
flowchart LR
    subgraph Today
      v2[v2 commerce service]
      v1[legacy v1 pipeline]
      osn[OpenSearch in server]
      rsync[Catalog sync on Redis]
      com[Commerce code in server]
    end
    subgraph Proposed
      eng[One Core search engine<br/>serving v1 and v2]
      osp[OpenSearch connector pack<br/>outside the server]
      ssync[Sync store SPI<br/>file · memory · Redis]
      cpk[Commerce logic in the commerce pack]
      more[More connector packs<br/>databases, SaaS, via ConnectorPackProvider]
    end
    v1 --> eng
    v2 --> eng
    osn --> osp
    rsync --> ssync
    com --> cpk
    osp --> more
```

Each arrow is a proposal in [remediation-proposals.md](remediation-proposals.md), with affected
modules, behaviour changes, migration steps and acceptance criteria. None of them is implemented.
New data sources should arrive as connector packs. They should not arrive as server modules,
which QB-287 removed.

## Boundaries

| Package | Responsibility |
|---|---|
| `commerce`, `connectors` | v2 search service, planning, backends |
| `api`, `pipeline`, `execution`, `analysis`, `decision`, `intent`, `semantic` | Legacy v1 analyze pipeline |
| `language`, `spelling`, `multilingual`, `translation`, `normalization` | Server adapters for the Core's language features |
| `threat`, `abuse`, `security` | Input safety, rate limits, identity and roles |
| `platform`, `extensions`, `plugins`, `domains` | Packs, licences, editions, plugins, agents |
| `streaming`, `event` | SSE delivery and pipeline events |
| `operations` | Health, metrics, production configuration validation |

The ADRs in [`docs/adr/`](../adr/) record the decisions behind these boundaries. ADR 0058 records
QB-287.

# QwenBridge

> **AI-native Search Decision Engine**

QwenBridge is a developer-first platform for understanding a user query,
reasoning about it with AI, producing an execution plan, and executing
that plan through a modular execution engine.

## Features

-   AI-first query understanding
-   Language detection
-   Intent analysis
-   Query rewriting
-   Semantic analysis
-   AI decision engine
-   Execution planning
-   Modular execution engine
-   Provider-based AI architecture
-   REST API

## Architecture

``` text
User Query
     │
Language
     │
Intent
     │
Policy / Threat
     │
Rewrite
     │
Semantic
     │
Decision
     │
Execution Plan
     │
Execution Engine
     │
Execution Result
```

## Pipeline

1.  Language Detection
2.  Intent Detection
3.  Policy Evaluation
4.  Threat Analysis
5.  Query Rewrite
6.  Semantic Analysis
7.  AI Decision
8.  Confidence Scoring
9.  Execution Plan Generation
10. Execution Engine
11. Execution Result

## Execution Engine

Supported operations:

-   Direct Answer
-   Keyword Search
-   Vector Search
-   Hybrid Search
-   Facet
-   Rerank
-   Return Results

## AI Stack

-   Qwen
-   BGE-M3
-   Ollama

## Project Structure

``` text
src/main/java/io/qwenbridge
├── ai
├── api
├── decision
├── execution
├── intent
├── pipeline
├── rewrite
├── semantic
└── model
```


## AI Provider Reliability

V6 launches with deterministic Ollama routing. Provider calls are bounded by
connect/read timeouts and a small retry count. QwenBridge does not perform
automatic provider failover in V6; an unavailable AI provider returns a
controlled `502 AI_PROVIDER_ERROR`.

See [`docs/architecture/provider-reliability.md`](docs/architecture/provider-reliability.md).

## REST API

Current public API version: `v1`

- `POST /api/v1/search/analyze`
- `GET /api/v1/search/stream/{requestId}`
- `POST /api/v1/ai/chat`

Response includes language, intent, rewrite, semantic analysis,
decision, execution plan, execution result, confidence, cache metadata,
and pipeline trace.

Streaming uses request-scoped server-sent events with stable v1 event names
and a frozen public event envelope. See `docs/api/sse.md`.

## Testing

``` bash
mvn clean test
```

## Roadmap

-   ✅ V1 Foundation
-   ✅ V2 AI-native Search Core
-   ⏳ V3 Real Search Providers
-   ⏳ V4 Retrieval Intelligence
-   ✅ V5 Production AI Search Platform
-   ⏳ V6 Public Product Hardening

## Design Principles

-   AI-first
-   Provider abstraction
-   Step-based pipeline
-   Immutable domain models
-   Extensible execution engine

## License

MIT

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

## REST API

`POST /api/search/analyze`

Response includes language, intent, rewrite, semantic analysis,
decision, execution plan, execution result, confidence, and pipeline
trace.

## Testing

``` bash
mvn clean test
```

## Roadmap

-   ✅ V1 Foundation
-   ✅ V2 AI-native Search Core
-   ⏳ V3 Real Search Providers
-   ⏳ V4 Retrieval Intelligence
-   ⏳ V5 Production AI Search Platform

## Design Principles

-   AI-first
-   Provider abstraction
-   Step-based pipeline
-   Immutable domain models
-   Extensible execution engine

## License

MIT

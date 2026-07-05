# QwenBridge

**QwenBridge is an AI-native search decision engine and developer platform.**

It accepts a user query, analyzes it through an AI-first pipeline, creates an execution plan, runs search operations through pluggable providers, and exposes the result through REST APIs, Server-Sent Events, Java SDK, Spring Boot Starter, and TypeScript SDK.

Current public release track: **V9 — Developer Platform**

## What QwenBridge does

QwenBridge combines query understanding, threat analysis, semantic reasoning, retrieval, ranking, reranking, streaming, and operational safety into one modular platform.

Core capabilities:

- AI-native query analysis
- language detection
- intent detection
- input normalization
- policy and threat analysis
- query rewrite
- semantic analysis
- AI decision making
- execution planning
- keyword, vector, and hybrid search
- ranking and reranking
- request-scoped SSE streaming
- provider abstraction for AI and search backends
- Redis-backed cache support
- OpenSearch integration
- production health, metrics, tracing, and logging
- Java SDK
- Spring Boot Starter
- TypeScript SDK

## Architecture

```text
User Query
    │
    ▼
Input Normalization
    │
    ▼
Language Detection
    │
    ▼
Intent Analysis
    │
    ▼
Policy and Threat Analysis
    │
    ▼
Rewrite
    │
    ▼
Semantic Analysis
    │
    ▼
AI Decision
    │
    ▼
Confidence Scoring
    │
    ▼
Execution Plan
    │
    ▼
Execution Engine
    │
    ├── Direct Answer
    ├── Keyword Search
    ├── Vector Search
    ├── Hybrid Search
    ├── Facet
    ├── Rerank
    └── Return Results
    │
    ▼
Execution Result
```

## Main modules

```text
qwenbridge-server
qwenbridge-java-sdk
qwenbridge-spring-boot-starter
qwenbridge-typescript-sdk
examples
docs
scripts
```

## AI and search stack

QwenBridge is provider-oriented. The default local stack uses:

- Qwen through Ollama
- BGE embeddings through Ollama
- OpenSearch for retrieval
- Redis for cache and rate limiting support
- Spring Boot for the server runtime

## Public APIs

Current public API version: `v1`

REST:

```text
POST /api/v1/search/analyze
POST /api/v1/ai/chat
GET  /api/v1/health
GET  /api/v1/version
```

Streaming:

```text
GET /api/v1/search/stream/{requestId}
```

The SSE API uses a stable public event envelope and typed event payloads.

API documentation:

- [REST API](docs/api/rest-api.md)
- [SSE API](docs/api/sse.md)

## SDKs and starters

Java SDK:

- [Java SDK README](qwenbridge-java-sdk/README.md)
- [Java SDK example](docs/examples/java-sdk-example.md)

Spring Boot Starter:

- [Spring Boot Starter README](qwenbridge-spring-boot-starter/README.md)
- [Spring Boot Starter example](docs/examples/spring-boot-starter-example.md)

TypeScript SDK:

- [TypeScript SDK README](qwenbridge-typescript-sdk/README.md)
- [TypeScript SDK publishing](qwenbridge-typescript-sdk/PUBLISHING.md)
- [TypeScript SDK example](docs/examples/typescript-sdk-example.md)

## Local development

Start dependencies:

```bash
docker compose up -d
```

Run the full Java verification suite:

```bash
mvn clean verify
```

Run the release verification script:

```bash
bash scripts/verify-release.sh
```

Development documentation:

- [Local Development](docs/development/local-development.md)
- [Testing](docs/development/testing.md)
- [Code Quality](docs/development/code-quality.md)
- [Branching and Pull Request Policy](docs/development/branching-and-pr-policy.md)

## Operations

Operational documentation:

- [Configuration](docs/operations/configuration.md)
- [Health](docs/operations/health.md)
- [Logging](docs/operations/logging.md)
- [Metrics](docs/operations/metrics.md)
- [Tracing](docs/operations/tracing.md)
- [Runbook](docs/operations/runbook.md)
- [Docker Deployment](docs/deployment/docker.md)

## Architecture documentation

- [Architecture Overview](docs/architecture/overview.md)
- [Modules](docs/architecture/modules.md)
- [Pipeline](docs/architecture/pipeline.md)
- [AI Stack](docs/architecture/ai-stack.md)
- [Architecture Rules](docs/architecture/architecture-rules.md)
- [Provider Reliability](docs/architecture/provider-reliability.md)
- [Ranking Policy](docs/architecture/ranking-policy.md)
- [Retrieval Verification](docs/architecture/retrieval-verification.md)

## Release documentation

- [Release History](docs/release/README.md)
- [V9 Roadmap](docs/roadmap/V9.md)
- [V9 Release Checklist](docs/release/V9-release-checklist.md)
- [V9 Release Evidence](docs/release/V9-release-evidence.md)
- [Maven Central Publishing](docs/publishing/maven-central.md)
- [npm Publishing](docs/publishing/npm.md)
- [Release Process](docs/publishing/release-process.md)

## Security

Security documentation:

- [Security Policy](SECURITY.md)
- [Abuse Protection](docs/security/abuse-protection.md)
- [Secrets Handling Policy](docs/security/secrets-handling-policy.md)

Do not commit secrets, tokens, passwords, private endpoints, generated build output, dependency folders, or local environment files.

## License

QwenBridge is licensed under the MIT License. See [LICENSE](LICENSE).

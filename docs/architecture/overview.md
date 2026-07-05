# Architecture Overview

QwenBridge is a modular AI-native search decision engine. It separates public API delivery, pipeline orchestration, AI providers, search providers, execution, streaming, and operations.

## Architectural goals

- Keep public API contracts stable.
- Keep AI and search vendors replaceable.
- Make pipeline behavior explicit and testable.
- Preserve request correlation across REST, providers, logs, and SSE.
- Fail safely when dependencies are unavailable.

## High-level flow

```text
REST request → normalization → pipeline analysis → execution plan
→ provider-backed execution → ranked result → REST response

Pipeline events → event publisher → SSE listener → typed SSE client events
```

## Boundaries

- `api`: HTTP contracts and request handling
- `pipeline`: ordered query-analysis orchestration
- `ai`: provider-neutral AI contracts and provider implementations
- `execution`: execution planning and operations
- `execution.provider`: provider-neutral retrieval boundary
- `streaming`: public SSE delivery and session lifecycle
- `operations`: health, metrics, tracing, and production validation
- `threat`: input-risk detection, scoring, correlation, and explanation

Read the ADRs in `docs/adr/` for decisions that establish these boundaries.

# ADR 0048: Chat, Embedding and Reranking Provider SPIs

## Status

Accepted (QwenBridge 1.0, Section 4 Phase 34: QB-243 – QB-248)

## Context

- **Embedding and reranking:** Core SPIs already existed (QB-110, QB-119).
- **Chat:** the server-only `AIProvider` mixed chat, streaming and embedding. It had no model
  identity, no capability declaration and no typed failures.
- **Selection:** the provider came from `qwenbridge.ai.provider`, with no fallback and no record
  of which model answered.

## Decision

1. **A Core chat SPI.** `ChatProvider`, with `ModelDescriptor`, `ChatCapabilities`, `ChatCall`
   (timeout, cancellation, output schema, streaming) and a typed `ChatResult`. The server's
   providers are adapted to it (`ChatRouting`). `AIProvider` stays as the server's transport
   interface; no second embedding or reranking interface is added.
2. **Capabilities before execution.** `AiCapabilities` checks chat, embedding-space and
   rerank-candidate requirements before any call. `Reranker` gains `maxCandidates()` and
   `model()` (with defaults), and `RerankStage` honours the candidate limit.
3. **Model identity.**
   - **Credentials:** model metadata refuses credential-like keys.
   - **Embeddings:** compatibility stays defined by `EmbeddingSpace`; indexes refuse other spaces.
   - **Fallback:** `FallbackEmbeddingProvider` refuses providers in another space.
4. **Explicit fallback.** `ChatRouter` tries fallbacks only after provider faults, never stitches
   streams, and reports who answered. The server enables it only with
   `qwenbridge.ai.fallback-providers`. Otherwise behaviour and errors are unchanged.
5. **Discovery.** `GET /api/v2/ai/models` shows models, capabilities and reindex conditions.

## Consequences

- **Optional, replaceable capabilities.** Chat, embedding and reranking are optional and
  independently replaceable. The default deployment behaves exactly as before.
- **Internal model calls.** Analysis, translation and the other model-assisted services call
  `AIService.chat`, so they gain the fallback when it is configured. They do not expose who
  answered; only `/api/v1/ai/chat` reports it in headers.
- **Version tracking needs an operator.** Ollama does not report a version. Model versions are
  configured (`qwenbridge.ai.chat.model-version`, `qwenbridge.embedding.model-version`), so an
  operator must raise them when a model changes in place.
- **AI provider packs.** Provider implementations can be packaged independently of Core: they
  depend only on the Core SPIs. Packaging them as installable packs uses the general pack
  lifecycle (Phase 36).

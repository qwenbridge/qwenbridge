# AI Providers: Chat, Embedding and Reranking

QwenBridge has three independent, optional AI capabilities. Each has one Core SPI with no
dependency on a provider. ADR 0048 records the decisions.

| Capability | Core SPI | Server implementations | Without it |
|---|---|---|---|
| Chat | `io.qwenbridge.core.ai.ChatProvider` (QB-243) | `ollama`, `http-json` (through `ChatRouting`) | Model-assisted stages report unavailable |
| Embedding | `io.qwenbridge.core.embedding.EmbeddingProvider` (QB-110, QB-244) | Ollama embeddings (`EmbeddingStages`) | `qwenbridge.embedding.enabled=false`: vector retrieval reports unavailable |
| Reranking | `io.qwenbridge.core.ranking.Reranker` (QB-119, QB-245) | Query-term reranker, Commerce reranker | The baseline order stands, labelled `DISABLED` |

## Model identity and configuration (QB-247)

- **Chat models:** `ModelDescriptor` names the provider, model, version and the configuration that
  affects output. It refuses configuration keys that look like credentials, so it can be shown and
  logged. Credentials stay in provider configuration (for example
  `qwenbridge.ai.http-json.token`).
- **Embeddings:** an `EmbeddingSpace` is the model, version and dimensions. Vectors are comparable
  only within one space:
  - an in-memory index refuses vectors from another space;
  - `IndexCompatibility` reports why a reindex is needed (`MODEL_CHANGED`, `VERSION_CHANGED`,
    `DIMENSIONS_CHANGED`).

### When to reindex

Rebuild every vector index after changing any of:
- `qwenbridge.ai.ollama.embedding-model`;
- `qwenbridge.embedding.model-version`;
- `qwenbridge.embedding.dimensions`.

Raise `qwenbridge.embedding.model-version` when the same model name is updated in place (for
example after a new `ollama pull`). That turns a silent change into a detected one.

## Capabilities before execution (QB-246)

`AiCapabilities` checks a request against a provider before calling it. Anything missing is
reported as `UNSUPPORTED` with the reason, and that provider is not called:

| Provider | Checked |
|---|---|
| Chat | Streaming, structured output, input length, output tokens |
| Embedding | Same space as the index, batch size |
| Reranking | Candidate count; `RerankStage` offers at most `maxCandidates()` items and keeps the rest in baseline order |

`GET /api/v2/ai/models` shows all of this.

## Selection and fallback (QB-248)

- **Selection.** `qwenbridge.ai.provider` selects the chat provider explicitly. Switching it changes
  no public API or QueryIR contract.
- **Fallback.** Fallback is off unless `qwenbridge.ai.fallback-providers` lists providers.
  - **When it runs:** a fallback is used only after a provider fault (unavailable, timeout, rate
    limit, missing capability). It never runs after a cancellation or an invalid request.
  - **Errors:** without fallbacks, the primary's error is returned unchanged. With fallbacks, if
    they all fail, the error lists every attempt.
  - **Labelling:** `X-QwenBridge-AI-Provider` names the provider that answered, and
    `X-QwenBridge-AI-Fallback: true` marks a fallback answer. A degraded answer is never presented
    as the primary's.
  - **Streaming never falls back:** once text has been streamed, a failure is returned rather than
    continued by another model.
  - **Embeddings never fall back in the server.** `FallbackEmbeddingProvider` accepts only providers
    in exactly the same space.
- **Authorization is unchanged.** Routing happens after authorization. Configuring providers or
  fallbacks grants nothing, and an authenticated deployment still refuses the tenant-less v1 chat.
- **Configuration:**

  | Setting | Default | Purpose |
  |---|---|---|
  | `qwenbridge.ai.chat.timeout` | 60 s | Timeout for each routed chat call |
  | `qwenbridge.ai.chat.model-version` | `unversioned` | Version of the primary chat model |
  | `qwenbridge.ai.chat.max-input-chars` | 32,000 | Largest accepted prompt |

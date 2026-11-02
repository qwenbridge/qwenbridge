# AI Stack

QwenBridge uses an AI Provider SPI (`io.qwenbridge.ai.provider`) to isolate application behaviour
from a concrete model runtime.

## Responsibilities

| Party | Owns |
|---|---|
| QwenBridge | Prompts, parsing, reliability policy, typed contracts, pipeline behaviour |
| The AI provider | Transport to the model runtime |

Ollama is the default local provider (`qwen2.5` for chat, `bge-m3` for embeddings). An HTTP JSON
gateway provider is the documented alternative (`QWENBRIDGE_AI_PROVIDER=http-json`).

## Capabilities

The provider boundary supports chat, embeddings and streaming chat. They are used for:
- **Analysis:** one structured analysis per query (`analysis.SearchAnalysisService`, cached in
  Redis when it is available).
- **Embeddings:** query and catalog embeddings for vector retrieval.
- **Translation:** model translation for multilingual expansion (`translation.ModelTranslationProvider`).
- **Reranking:** optional, through a reranker plugin.

Provider failures become typed application failures, with request correlation and safe
diagnostics. See [AI providers](ai-providers.md) and [provider reliability](provider-reliability.md).

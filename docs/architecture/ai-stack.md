# AI Stack

QwenBridge follows an AI-first, provider-agnostic architecture.

## Current Stack

Ollama ↓ Qwen ↓ BGE-M3 ↓ QwenBridge Pipeline

## Responsibilities

### Qwen

-   Intent analysis
-   Query rewrite
-   Decision generation
-   Structured JSON output

### BGE-M3

-   Embeddings
-   Semantic similarity
-   Semantic validation

### Ollama

-   Local model runtime

The provider abstraction allows replacing models without changing
business logic.

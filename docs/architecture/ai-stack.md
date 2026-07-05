# AI Stack

QwenBridge uses an AI Provider SPI to isolate application behavior from a concrete model runtime.

## Responsibilities

- QwenBridge owns prompts, parsing, reliability policy, typed contracts, and pipeline behavior.
- The AI provider owns transport to the model runtime.
- Ollama is the default local provider implementation.

## AI capabilities

The provider boundary supports chat, embeddings, and streaming chat. Higher-level services use those capabilities for query analysis, rewrite, intent, semantic analysis, and decisions.

Provider failures are converted into application-level failures with request correlation and safe diagnostics.

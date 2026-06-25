# QwenBridge

QwenBridge is a developer-first AI query interpretation pipeline.

It turns a user query into a structured execution context using a step-based pipeline.

QwenBridge is not a generic document search engine, not a RAG chatbot, and not a multimedia search platform.

## Current pipeline

- Language detection
- Intent detection
- Policy evaluation
- Threat analysis
- Query rewriting
- Semantic analysis
- Decisioning
- Confidence scoring
- Pipeline tracing

## AI stack

The current default stack is:

- Qwen for chat/reasoning
- BGE-M3 for embeddings
- Ollama as the local model runtime

The architecture is provider-based, so models and runtimes can be replaced without changing the core pipeline.

# AI Stack

QwenBridge is AI-native but vendor agnostic.

The current V1 implementation uses mock services.

Future versions will integrate real AI models.

## Planned Stack

Ollama

↓

Qwen

↓

BGE-M3

## Ollama

Responsible for local model execution.

## Qwen

Responsibilities

- Query Rewrite
- Intent Detection
- Query Expansion
- Canonical Query
- JSON Generation

Qwen generates candidate rewrites.

## BGE-M3

Responsibilities

- Semantic Understanding
- Multilingual Understanding
- Semantic Validation
- Semantic Drift Detection

BGE-M3 never rewrites text.

It validates the meaning of rewritten queries.

## Flow

User Query

↓

Qwen

↓

Candidate Queries

↓

BGE-M3

↓

Semantic Validation

↓

Rules

↓

Decision

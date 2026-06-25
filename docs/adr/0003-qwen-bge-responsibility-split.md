# ADR 0003 - Qwen and BGE-M3 Responsibility Split

## Status

Accepted

## Context

A single Large Language Model is very good at generating text, but it is not always reliable at preserving semantic meaning.

Query rewriting requires two different capabilities:

1. Generate better candidate queries.
2. Verify that the rewritten query still represents the user's original intent.

Using one model for both tasks increases the risk of semantic drift.

## Decision

Separate generation from validation.

Generation and validation are different responsibilities.

Qwen is responsible for generation.

BGE-M3 is responsible for semantic validation.

## Responsibilities

### Qwen

- Query Rewrite
- Query Expansion
- Canonical Query Generation
- Intent Detection
- Structured JSON Output

Qwen is allowed to be creative.

### BGE-M3

- Semantic Similarity
- Meaning Validation
- Semantic Drift Detection
- Multilingual Embeddings

BGE-M3 never rewrites user input.

It only evaluates semantic preservation.

## Pipeline

User Query

↓

Qwen

↓

Candidate Rewrites

↓

BGE-M3

↓

Semantic Validation

↓

Business Rules

↓

Decision

## Consequences

Advantages

- Better rewrite quality
- Lower semantic drift
- Easier model replacement
- Independent benchmarking
- Vendor independence

Either model can be replaced without changing the pipeline architecture.

## Future

Future versions may replace either model independently.

Examples:

- Llama
- Mistral
- GPT
- Gemini
- Jina Embeddings
- E5
- Voyage
- Nomic

The architecture remains unchanged.

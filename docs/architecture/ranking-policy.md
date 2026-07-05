# Ranking Policy

Ranking gives QwenBridge a deterministic scoring layer before optional reranking.

## Goals

- make result ordering explicit
- support hybrid retrieval
- keep scoring explainable
- allow safe fallback when reranking is unavailable
- make quality changes testable

## Ranking score

A ranking score should consider available provider signals such as:

- keyword match strength
- vector similarity
- hybrid score
- source confidence
- result metadata

## Reranking

Reranking is an optional second-stage operation. If reranking fails or is disabled, QwenBridge must safely return the ranked result set rather than fail the whole request unless the execution policy explicitly requires reranking.

## Testing

Ranking behavior must be covered by unit tests and benchmark evaluation where relevant.

# Ranking Policy

## Overview

V7 introduces a formal ranking foundation for QwenBridge retrieval quality.

The ranking policy converts raw retrieval signals into a normalized final ranking score.

## Score Components

| Component | Meaning |
| --- | --- |
| `lexicalScore` | Keyword or BM25-style score normalized to `[0.0, 1.0]`. |
| `vectorScore` | Embedding similarity score normalized to `[0.0, 1.0]`. |
| `metadataBoost` | Business/category/metadata boost normalized to `[0.0, 1.0]`. |
| `freshnessBoost` | Recency boost reserved for future use, normalized to `[0.0, 1.0]`. |
| `finalScore` | Weighted final ranking score normalized to `[0.0, 1.0]`. |

## Default Weights

| Signal | Weight |
| --- | --- |
| Lexical | `0.45` |
| Vector | `0.45` |
| Metadata | `0.07` |
| Freshness | `0.03` |

## Normalization Rules

All component scores are clamped to the `[0.0, 1.0]` range.

Invalid numeric values such as `NaN` and infinity are treated as `0.0`.

If no explicit `lexicalScore` metadata exists, the provider `SearchHit.score` is used as the lexical fallback.

## Current Scope

This foundation defines the scoring model and default policy.

Future V7 slices will connect the ranking policy to hybrid retrieval results and reranking.

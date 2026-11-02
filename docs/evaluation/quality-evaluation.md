# Retrieval quality evaluation

## Datasets (QB-207)

| Dataset | File | Contents |
|---|---|---|
| multidomain 1.0 | `evaluation/datasets/multidomain-1.0.json` | 36 documents (Commerce products, DevOps runbooks, Security playbooks) in English, Persian and Swedish. 36 queries (27 held-out, 9 tuning): 6 exact, 6 semantic, 18 multilingual, 3 mixed-language, 3 negative. Graded judgments 0–3 |
| library 1.0 | `qwenbridge-core/src/test/resources/datasets/library-1.0.json` | Structured and Unicode retrieval cases with exact expected sets |
| commerce-demo 1.0 | `qwenbridge-server/src/main/resources/datasets/commerce-demo-1.0.json` | Constraint-level expected results per contract |

**Judgment limits.** All texts are synthetic. A single annotator, from the QwenBridge 1.0 work,
labelled the judgments. They are a reproducible seed set, not an independent human-labelled
benchmark (audit prerequisite H7). The numbers describe retrieval behaviour on this set and are not
a claim about production relevance.

## How to reproduce (QB-209, QB-210)

| What | Command | Needs |
|---|---|---|
| Keyword (BM25), offline | `mvn -pl qwenbridge-core test -Dtest=KeywordQualityBaselineTest` → `qwenbridge-core/target/evaluation/keyword-core.json` | nothing |
| Keyword vs vector vs hybrid | `QWENBRIDGE_RUN_OLLAMA_IT=true mvn -pl qwenbridge-server test -Dtest=RetrievalQualityEvaluationTest` → `qwenbridge-server/target/evaluation/quality-*.json`, `quality-comparison.md` | the configured embedding model |

**Retrieval** is the Core implementation the platform uses: BM25, cosine kNN over the configured
embeddings, and reciprocal-rank fusion (k=60) through `HybridRetriever`. The comparison measures
K=5 on the held-out split. Both tests also run in every master verification.

## Results measured on 2026-11-01

- **Environment:** a local Apple M4 Pro, Ollama `bge-m3` (1024 dimensions), 36 documents.
- **Recency:** the master verification log of each run holds the current numbers; these are the
  first measurement.

| Strategy | P@5 | R@5 | MRR | nDCG@5 | Coverage | Negative FP rate | Degraded |
|---|---|---|---|---|---|---|---|
| keyword | 0.225 | 0.799 | 0.889 | 0.809 | 0.958 | 0.00 | 0 |
| vector | 0.325 | 1.000 | 1.000 | 0.984 | 1.000 | 1.00 | 0 |
| hybrid | 0.308 | 0.972 | 0.979 | 0.935 | 1.000 | 1.00 | 0 |

| nDCG@5 | en | fa | sv | mixed | exact | semantic | multilingual | mixed-language |
|---|---|---|---|---|---|---|---|---|
| keyword | 0.876 | 0.900 | 0.737 | 0.567 | 0.976 | 0.676 | 0.818 | 0.567 |
| vector | 0.995 | 1.000 | 0.995 | 0.898 | 0.996 | 0.994 | 0.998 | 0.898 |
| hybrid | 0.936 | 0.993 | 0.962 | 0.761 | 0.996 | 0.817 | 0.977 | 0.761 |

## What the numbers say, and what they do not

- **Hybrid is not the best strategy here.** Pure vector retrieval ranks best on this set. Fusion
  pulls in keyword results that are worse for paraphrased ("semantic") and mixed-language queries
  (nDCG 0.817 vs 0.994, and 0.761 vs 0.898). Hybrid helps when keyword and vector agree; on this
  small, cleanly separated set the multilingual model rarely needs help.
- **Vector retrieval always returns K results.** It has no relevance threshold, so for queries with
  no answer ("garden lawn mower") it fills every slot (negative FP rate 1.00). Keyword returns
  nothing for them. A deployment that needs "no result" answers must add a similarity threshold or
  rely on hard constraints.
- **Keyword's weak spots are mixed-language queries (0.567) and Swedish (0.737).** In those cases
  query terms are in another language or inflected form than the document's.
- **Precision@5 is capped by the set.** Queries have at most 3 relevant documents, so precision
  cannot exceed 0.6.
- **Not representative.** 36 short, topically distinct documents are far easier than a real
  catalog. These results must not be extrapolated to production scale or production relevance.

## Capability matrix (QB-208)

`evaluation/capability-matrix.json` maps 11 capabilities to existing tests, each with positive,
negative and degradation cases:
- exact, structured, semantic, hybrid;
- multilingual, mixed-language, multi-intent;
- negative, ambiguous, unsupported;
- provider availability.

`CapabilityMatrixTest` fails if a referenced test does not exist. Missing coverage is listed as an
explicit gap. Currently there is one: no test makes one sub-query's provider fail while the others
succeed (partial multi-intent degradation).

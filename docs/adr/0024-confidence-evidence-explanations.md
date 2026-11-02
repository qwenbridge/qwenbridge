# ADR 0024: Typed Confidence, Versioned Evidence and Grounded Explanations

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 12: QB-078 – QB-084)

## Context

Before this phase:

- `ConfidenceAssessment` reduced the model's intent, semantic and decision
  scores to one number.
- Commerce's approved-search path copied that single aggregate into all three
  signals, so it reported scores no model had produced.
- Evidence was a Commerce record held in a Redis-only store.
- Only threat findings were explained.
- Calibration code (Brier and ECE) lived only in Commerce.

## Decision

1. **Typed confidence (QB-078).** `ConfidenceReport` lists one
   `ComponentConfidence` per component that ran, each with its source:
   - language, intent, threat and translation;
   - the model's intent, semantic and decision scores.

   Each component uses the Core `Confidence` kinds: UNKNOWN, DETERMINISTIC,
   HEURISTIC, MODEL_SCORE and MODEL_DECISION. The overall value follows the
   `weakest-link-v1` rule:

   | Components | Overall |
   |---|---|
   | Any UNKNOWN | UNKNOWN |
   | All MODEL_SCORE | MODEL_SCORE, the minimum score |
   | All DETERMINISTIC | DETERMINISTIC |
   | Any other mix | HEURISTIC, with no number |

   `calibrated` is true only when every component is a calibrated score. The v1
   response gains `confidenceReport`.
2. **Retrieval scores are separate (QB-079).**
   - `RetrievalScore` (BM25, vector similarity, fusion, rerank position, backend
     native) is its own type with no conversion to `Confidence`. Scores are
     comparable only within one scorer.
   - Commerce's approved-search path now carries the recorded model signals
     instead of copying the aggregate into each one.
3. **Versioned evidence (QB-080).**
   - `EvidenceRecord` (version `1.0`) records the kind, source type and id, a
     span into the original query or a SHA-256 digest of other content, the
     observation time, a parent (the provenance chain) and non-sensitive
     attributes. It never copies content.
   - `EvidenceSet` rejects duplicate ids, unknown parents and cycles, and
     checks that spans are grounded in the original query.
   - `EvidenceStore` is an optional storage SPI, and `InMemoryEvidenceStore` is
     its default. Records are tenant-scoped. Commerce's Redis store stays a
     connector concern.
4. **Explanations from recorded decisions (QB-081).**
   - `QueryIrExplainer` explains intents, entities, constraints and semantic
     signals from what was recorded: span, provenance, the extractor's recorded
     reason, strength, and the validation gate's verdict with violation codes.
   - The server adds threat explanations (from the threat assessment) and plan
     explanations (mode, backend and step count; the model's free-text reason
     is cited only by digest).
   - Statements are filled-in templates; nothing is generated. The v1 response
     gains `explanations`. Evidence is given as offsets and digests, never text.
5. **No unsupported explanations (QB-082).**
   - An `Explanation` is SUPPORTED only with a statement and at least one cited
     piece of evidence. UNKNOWN and INSUFFICIENT_EVIDENCE explanations cannot
     carry a statement.
   - `ExplanationSet.checked` downgrades any supported explanation whose
     evidence is missing or whose span no longer matches the query.
   - Inferred elements without a span, and decisions with nothing recorded, are
     not narrated.
6. **Calibration (QB-083).**
   - `core.confidence.Calibration` computes the Brier score, the reliability
     bins and ECE. ECE is withheld below 50 observations, and the report says
     why. Commerce's evaluation endpoint delegates to it, with unchanged
     results.
   - The opt-in live Commerce test now records the real model's intent score
     on its 31 labelled cases and writes `target/calibration-report.json`. With
     31 observations, ECE is honestly withheld.
   - A meaningful calibration claim needs a larger labelled set, which is an
     external prerequisite (H7). No calibration dataset was invented.
7. **Tests (QB-084).** Tests cover:
   - the combination rules;
   - retrieval-score separation;
   - calibration (perfect, overconfident, too few observations);
   - the evidence invariants and tenant-scoped storage;
   - grounded explanations through the real extractor and validation gate;
   - the downgrade of ungrounded claims;
   - secret masking: a quoted fragment that holds only a secret's value (the
     token after `token=`) is masked using `Redactor.secretValues` of the full
     query.

## Consequences

- Clients can tell a measured model score from a heuristic or an unknown, and
  never receive an aggregate disguised as a probability.
- Explanations are only as rich as what is recorded. Recording more decisions
  is how explanations improve; generating prose is not.
- Calibrated confidence stays `calibrated: false` until a representative
  labelled set exists.

# ADR 0017: Domain-Neutral Intent Understanding

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 4: QB-022 – QB-028)

## Context

Before this decision:

- The only live intent came from the LLM analysis: a Commerce-shaped enum
  (`PRODUCT_SEARCH`, `NAVIGATION`, `FILTER`, `COMPARE`, `UNKNOWN`). It allowed
  one intent per request, carried no text evidence and had no version.
- `IntentService` fell back silently to `PRODUCT_SEARCH` at 0.5 confidence on
  any failure, and logged the raw query.
- Clarification depended only on LLM signals and used a generic English
  question.

## Decision

### Taxonomy (`core.intent.IntentTaxonomy`, version 1.0)

- Core intents: `search`, `compare`, `analyze`, `explain`, `investigate`,
  `refine`, `navigate`, each with a documented boundary.
- Packs add namespaced intents (`commerce.reorder`) that must name a core
  parent.
- Legacy names map onto the taxonomy:
  - `PRODUCT_SEARCH` → `search`
  - `FILTER` → `refine`
  - `NAVIGATION` → `navigate`
  - `COMPARE` → `compare`
  - `UNKNOWN` → no intent

### Deterministic analysis (`core.intent.IntentAnalyzer`)

It needs no model:

- **Intents.** Multilingual cue words (EN/SV/FA) on word boundaries, longest
  match first. Every intent records its span and `HEURISTIC` confidence, and a
  request can have several.
  - Content without a cue is an **implicit search** citing the whole text.
    That is recorded as such, never a silent default.
- **Retrieval needs.** Overlapping hints:
  - identifiers (SKUs, IPs, UUIDs, quoted text) → `EXACT`;
  - comparisons, measures or negation → `STRUCTURED`;
  - subjective cues or descriptive text → `SEMANTIC`;
  - content words → `LEXICAL`.

  No backend is chosen.
- **Decomposition.**
  - Sequential steps ("and then", "och sedan", "و سپس") depend on the
    previous step.
  - Parallel items ("and a …", "och en …", "و یک …") are independent.
  - A shared tail (", both …") applies to all items.
  - The output is QueryIR `SubQuery`s, acyclic by construction.
- **Assessment.**
  - `CLEAR` when the request can be executed as understood.
  - `NEEDS_CLARIFICATION` for vague-only requests ("Find the best option").
  - `OUT_OF_DOMAIN` when every domain the client hinted is not enabled.
  - Each issue yields one focused question in the request's language
    (en/sv/fa).
  - Missing preferences (size, price) are **not** a reason to ask.

### Pipeline

- `IntentStep` stores the `IntentUnderstanding`. The v1 `intent` field keeps
  its legacy values (`UNKNOWN` when no AI analysis exists).
- `DecisionStep` clarifies on the deterministic assessment **before** using
  model signals. A confident model therefore cannot push a vague request into
  execution.
- Responses gain the additive `intentAnalysis` object. The Java SDK fixture
  was refreshed; the contract test enforced this.
- `IntentService` reports `UNKNOWN` on failure and no longer logs the query.

### Unicode threat rules (defect found while testing Persian requests)

**Problem.** The threat rules flagged every ZWNJ/ZWJ and every `\p{Cf}`
character. That **blocked correctly written Persian** ("می‌خوام") and emoji
ZWJ sequences.

**Fix.** `UnicodeObfuscationDetector` now removes only joiners required by
legitimate orthography before the rules run. It checks code points, because
Java regex lookbehind mishandles supplementary characters:

- joiners between letters of joining scripts (Persian, Urdu, Arabic, Indic and
  similar);
- ZWJ inside emoji sequences;
- tags inside well-formed subdivision flags.

**What is still flagged:**

- any remaining ZWNJ/ZWJ, for example inside Latin words;
- zero-width space, word joiner and BOM;
- soft hyphen;
- bidi overrides and isolates (HIGH).

**Newly flagged:** Unicode tag characters (hidden-instruction smuggling), as
HIGH.

**Now allowed:** the LRM/RLM/ALM direction marks, which cannot reorder text.

## Limitations

- The cue lists are a baseline, not full morphology. For example, the Swedish
  definite form "alternativet" had to be added explicitly. Languages other
  than EN/SV/FA rely on content-word detection only.
- Out-of-domain detection uses explicit domain hints. Open-vocabulary domain
  detection needs domain vocabularies from Domain Packs (Section 4).
- Contradictory constraints are detected in Phase 8 (constraint validation),
  not here.
- Measured intent accuracy needs a labelled dataset (QB-070/QB-207).

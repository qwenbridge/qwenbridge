# ADR 0020: Optional, Provider-Neutral Translation With Preservation Checks

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 3: QB-015 – QB-021)

## Context

Before this change, translation existed only inside Commerce:

- It was a private prompt in `CommerceSearchService`.
- It threw `IllegalArgumentException("Translation unavailable")` when the
  model gave no usable answer, which surfaced as a client error.
- Its preservation check compared digit strings and a fixed list of nine brand
  names. Units, operators, negation and currencies were not checked.
- Nothing recorded where the translated text came from.
- Cross-language retrieval existed only as Commerce's `catalogLanguage`.

## Decision

`core.translation` holds the contract. `core.retrieval` holds the
cross-language decision. Neither has any dependency beyond Core.

1. **SPI (QB-015).** `TranslationProvider` has an `id()`, declared
   `TranslationCapabilities` and a `translate(TranslationRequest)` method.
   - Capabilities: source and target languages, whether the provider detects
     the source language, and the maximum input size.
   - A request carries the source, the target and a timeout.
   - The result is a sealed `TranslationResult`: `Translated`, `Skipped` or
     `Failed` with a typed `Failure`. Failures are values, never exceptions.
   - `mul` marks code-switched text. `und` is only sent to a provider that
     detects languages, so nothing is guessed.
2. **Optional stage (QB-016).** `TranslationStage` works with or without a
   provider. Without one, every request gets `Failed(NO_PROVIDER)` (reported as
   `UNSUPPORTED`) and the original text is used. The stage itself:
   - skips same-language and not-requested translations;
   - checks capabilities and input size before calling the provider;
   - enforces the timeout on a virtual thread;
   - turns exceptions into `PROVIDER_ERROR`;
   - rejects blank, wrong-language or implausibly long output as
     `INVALID_OUTPUT`.
3. **Text representations (QB-017).** `TextRepresentations` keeps three forms
   apart: the original, the search-normalized form and the accepted
   translation. Each derivation is recorded as a `Transformation` (kind, from,
   to, actor, details). The original is never replaced: QueryIR evidence spans
   always point into it.
4. **Preservation (QB-018).** `PreservationChecker` compares the source and the
   translation as multisets per kind and reports `LOST` and `ADDED` issues.
   - **Checked in every language:** lost numbers (in any digit script), unit
     symbols next to numbers, dictionary entities, and ASCII identifiers. IPs
     and UUIDs must survive verbatim.
   - **Checked only when both languages are in the extractor's cue lexicon (en,
     sv, fa):** operators, negations, currencies, and added numbers or entities.
   - For other languages these kinds are reported as `unverified`
     (`PARTIALLY_VERIFIED`), never as preserved.
   - A currency word that is ambiguous in one language (for example "kronor"
     kept in an English translation of Swedish) is resolved with the other
     side's language. A translator that resolves an ambiguous currency itself
     is reported as having added one.
   - Under `Policy.ENFORCE` a changed translation becomes
     `Failed(NOT_PRESERVED)`.
5. **Confidence (QB-019).** A translation carries the Core `Confidence` that
   its provider reported. The model provider reports `MODEL_DECISION` with no
   number. A number appears only as `MODEL_SCORE` from a real score. The stage
   never invents or upgrades confidence.
6. **Cross-language retrieval (QB-020).**
   - Query side: `RetrievalHints.crossLanguage` and `contentLanguages` are set
     by `CrossLanguageRetrieval.withContentLanguages`.
   - Provider side: `ContentLanguageSupport` (indexed languages, multilingual
     semantic matching) and the translation provider's capabilities.
   - `CrossLanguageRetrieval.decide` returns one of `NOT_NEEDED`,
     `MULTILINGUAL_SEMANTIC`, `TRANSLATE_QUERY` (with target languages) or
     `UNSUPPORTED`, together with the reason. It never silently searches only
     the query's own language.
7. **Contract vs quality (QB-021).**
   - `TranslationConformance.verify` checks provider behaviour offline: id,
     capabilities, typed results, the requested target, the reported source,
     and no exceptions.
   - A JSON corpus (`translation/preservation-cases.json`) pins the
     preservation rules, including code-switched, CJK and uncovered-language
     cases.
   - Neither calls a model. Model translation quality is measured only by the
     opt-in live tests (`QWENBRIDGE_RUN_OLLAMA_IT`).

### Server

- **Model provider.** `ModelTranslationProvider` implements the SPI with the
  former Commerce prompt. The text is sent as JSON data and the answer is
  parsed against a schema.
- **Configuration.** `TranslationStages` builds the stages from
  `qwenbridge.translation.provider` (`none` by default, or `model`) and
  `qwenbridge.translation.timeout` (default 30s).
- **v1 pipeline.** `TranslationStep` (order 27) runs only when the request
  context has a `content_language` entry.
  - It runs after the threat and model-safety steps, so a blocked query never
    reaches a provider.
  - It translates the normalized text under `ENFORCE`.
  - The response gains `translation` (status, reason, provider, languages,
    confidence, and preservation kinds and counts without user values) and
    `textRepresentations`.
  - `QueryIrStep` marks the QueryIR as cross-language.
  - Translation is informational in v1, like the QueryIR.
- **Commerce.** Commerce translates through `TranslationStages.model()` under
  `REPORT`. A failure is now an explicit `"Translation unavailable"` issue,
  which leads to `CLARIFICATION_REQUIRED` instead of an exception.
  - Commerce then judges the final catalog-language text with the Core
    checker. It counts only losses (numbers, brands, ASCII identifiers, and
    currencies for covered languages), because Commerce appends constraint
    phrases to the translated text after translating (`render`), so added text
    is expected.
  - The issue messages are unchanged.

## Consequences

- The server starts and answers with no translation service. Translation is
  never a mandatory external dependency.
- Preservation rules are shared and domain-neutral. Adding a language to the
  extractor's cue lexicon extends the strict checks to that language.
- Operators and negations in languages outside the lexicon are reported as
  unverified, not checked. Live quality runs measure them.
- Commerce now catches lost currencies and identifiers as well. It still does
  not reject added operators or negations, because its rendering step adds
  them. This is a deliberate, documented difference from the v1 path.
- Only the v1 pipeline sets cross-language hints today. The Section 2 planner
  consumes `CrossLanguageRetrieval.decide` together with connector-declared
  `ContentLanguageSupport`.

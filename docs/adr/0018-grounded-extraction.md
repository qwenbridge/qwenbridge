# ADR 0018: Grounded, Domain-Neutral Extraction into QueryIR

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 5: QB-029 – QB-035)

## Context

Two extractors existed:

- `intelligence.extraction.QueryEntityConstraintExtractor` handled only
  English "under / below / up to" prices.
- Commerce's `GroundedCommerceExtraction` hard-coded Commerce vocabulary
  (colors, brands, categories) and resolved "کرون" (krona) to SEK without
  justification.

Neither produced the universal QueryIR, and neither separated hard from soft
constraints.

## Decision

`core.extraction.ConstraintExtractor` is the deterministic, domain-neutral
extractor. It covers English, Swedish and Persian, and numbers in any digit
script. Its output is QueryIR `Entity`, `Constraint` and `SemanticSignal`
elements, plus typed `Unresolved` notes.

### Grounding

- Every element has a span in the original text.
- Identifiers become entities: IPv4, UUID, codes and quoted phrases.
- Countries come from the JDK's CLDR names in EN/SV/FA and map to ISO
  3166-1 codes. Country names that are common English words are excluded.
  Cities are not resolved.
- Domain vocabulary comes from an `EntityDictionary` supplied by packs. Core
  contains no brand, color or material words.
- A bare number with no comparison word is never a constraint.

### Values

- `Numerals` parses any decimal digit script into an exact `BigDecimal`.
  - The Persian ٫ and ٬ separators are unambiguous.
  - For `.` and `,` the request language decides. A single separator before
    three digits is reported as `AMBIGUOUS_NUMBER`.
- `Units` resolves units, and resolves currencies only when justified.
  - ISO codes and unambiguous words (euro, یورو) resolve directly.
  - krona/kronor/کرون resolves only through a Swedish, Norwegian, Danish or
    Icelandic language or region. The dollar resolves only through a US, CA,
    AU or NZ region. Otherwise the currency stays empty and
    `AMBIGUOUS_CURRENCY` is reported.
  - No exchange rates are ever used.
- **Comparisons:** under/below/less than, up to/at most, above/over/more than,
  at least, "between … and …", and the SV/FA equivalents.
- **Approximations** ("around", "ungefär", "حدود") are SOFT equalities.
- **Time:**
  - Relative windows (last N minutes/hours/days/weeks; SV "senaste …"; FA
    "… گذشته") become `RELATIVE_PAST` durations. They are resolved only
    against an explicit reference time (the request time in the pipeline);
    otherwise `MISSING_REFERENCE_TIME` is reported.
  - Calendar words (today, yesterday) need a time zone, which clients may
    send as the `timezone` context entry. Otherwise `MISSING_TIME_ZONE` is
    reported.
  - ISO date bounds (after/before 2026-10-31) are also handled.

### Field binding

1. A schema field named just before the value ("memory usage above 85%" →
   `memory_usage`, "bot score below 10" → `bot_score`).
2. Otherwise the only schema field of a compatible type and unit.
3. Otherwise a proposed name, reported as `UNBOUND_FIELD`:
   - numbers and quantities take the preceding words;
   - money and times use `amount` and `time`, so a nearby noun ("shoes under
     1200 SEK") is never taken for a field.

Schema validation (Phase 8) decides whether a proposal is executable.

### Hard, soft and negation

- Explicit operators and exclusions are HARD, and the reason is always
  recorded. Preference wording (preferably, helst, ترجیحاً) or approximation
  makes them SOFT.
- Exclusions (not, without, excluding, inte, utan, بدون, غیر …) become
  negative operators:
  - when the target is in a schema field's closed vocabulary, that field
    with `NE`;
  - when it is a dictionary term, the term's field with `NE`;
  - otherwise `content NOT_CONTAINS <target>`.

  A negation is never dropped and never turned into a preference.
  "no more than" is a bound, not an exclusion.
- Subjective words (comfortable, lightweight, suspicious, unusual,
  increased …; SV and FA equivalents) are semantic signals with polarity
  ("not suspicious" → NEGATIVE). They are never filters.

### Pipeline

- `QueryIrStep` (order 31) assembles QueryIR from language, intents and
  extraction, validates it, and stores a `QueryIrResult`.
- It emits no SSE events, so the public event stream is unchanged.
- The schema of the first enabled hinted domain binds fields.
- Responses gain the additive `queryIR` (canonical QueryIR JSON with exact
  decimals) and `unresolved` fields. The SDK fixture was refreshed, enforced by
  the contract test.

## Consequences and limitations

- In v1 the QueryIR is informational; the legacy pipeline still plans with
  its own analysis. Planning on QueryIR starts in Section 2 (QB-099 – QB-106).
- The Commerce pack declares price as `NUMBER` plus a separate currency
  field, so money values need Phase 8 semantic mapping to bind there.
- The legacy extractors remain until their consumers move: the structured
  price path in Phase 8, and Commerce in Sections 2–3.
- Cue lists are a baseline; morphology is limited (as in ADR 0017). Measured
  extraction quality needs a labelled dataset (QB-207).

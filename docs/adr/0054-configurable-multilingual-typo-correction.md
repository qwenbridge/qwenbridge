# ADR 0054: Configurable Multilingual Typo Correction

## Status

Accepted (QB-291).

## Decision

1. **Core engine and SPI** (`io.qwenbridge.core.spelling`): `TypoCorrector`,
   `CorrectionProvider`, a language-neutral `DictionaryCorrectionProvider`, and `ProtectedTokens`.
   The Core contains no language list and no language data.
2. **Explicit enablement.** Off by default, no languages by default, and `SUGGEST_ONLY` unless a
   correcting policy is chosen. Configured languages are validated against BCP 47 and the installed
   providers, and never substituted.
3. **Tenant settings** come from configuration (`qwenbridge.typo.tenants`), with no storage. There
   are no request-level language settings.
4. **Detection guards.** A reliably detected, non-enabled language disables correction, unless an
   enabled language knows at least half of the query's words. Typos mislead detectors.
5. **Integration that keeps grounding intact.**
   - **v1:** corrects the search form.
   - **v2:** uses the correction as retrieval text only without a model rewrite.
   - **Constraints:** they are always extracted from the original text.
   - **Threat detection:** it always sees the original.
6. **Contracts.** QueryIR 1.1 adds the optional `correction`; 1.0 documents are unchanged. API and
   SSE responses gain `spelling` when enabled, and both SDKs are typed for it. A new endpoint,
   `GET /api/v2/spelling`, reports the effective settings.

## Consequences

- **Default deployments are unchanged:** no field, no correction, no cost beyond one disabled
  check.
- **Quality depends on dictionaries.** Applications must supply dictionaries per language, and
  their quality decides accuracy. The fixture-based accuracy report measures only the engine's
  decision logic.
- **Two contract tests changed on purpose.** The QueryIR shape test and the "newer minor version is
  rejected" test changed with the version decision. The latter now uses 1.2 as its example.

# ADR 0014: Language Detection and Normalization

## Status

Accepted (QwenBridge 1.0, Section 1 Phase 2: QB-008 – QB-014)

## Context

Before this decision:

- Short Latin-script queries (four words or fewer), and any unidentified Latin
  text, were reported as English.
- A client-declared language replaced detection, contrary to ADR 0013.
- One normalized text was used both for threat detection and for search/AI
  input. It was NFKC-normalized and URL/HTML-decoded up to three times. That
  helps expose obfuscated attacks, but it rewrites identifiers (full-width
  product codes, superscripts, ligatures) and double-decodes user text.

Measurements during implementation showed that Lingua in low-accuracy mode is
unreliable on short text: "table" → fr, "desk" → fi, "cheap office chair" → tl,
"Nike" → sq. Its confidence values were 1.0 even for these wrong answers.

## Decision

### Language detection (`LanguageService`, Core `LanguageDetector` SPI)

1. Detection order:
   1. Unambiguous script rules (Core `ScriptHeuristicLanguageDetector`):
      Hangul, kana, Hebrew, Thai, Greek, Armenian, Georgian, Arabic script
      with Persian- or Urdu-specific letters, plus Hindi marker words.
   2. Lingua on the dominant non-Latin part.
   3. Lingua on the whole text.
2. Lingua is consulted only with enough evidence: at least 4 words, or 4
   letters in scripts written without spaces. Shorter text stays `und`
   unless a script rule decided.
3. There is no default language. Undetermined text is reported as `und` with
   confidence `UNKNOWN`, and as `"unknown"` in the legacy v1 `language`
   field.
4. Lingua results carry confidence kind `MODEL_DECISION` without a number,
   because its scores are not informative. A number is only ever reported
   with kind `MODEL_SCORE`, and is never presented as a probability.
5. If the model is disabled (`qwenbridge.language.model-enabled=false`) or
   fails, the deterministic script detector is used.
6. Mixed input is segmented by script, and each segment is detected
   separately. Text is *mixed* when either:
   - two segments have different determined languages, or
   - a determined segment coexists with a substantial segment (2 or more
     words) in another script.

   Mixed text has an overall detection of `und`. The legacy `language` field
   reports the language with the most letters, and `languageAnalysis.segments`
   reports every segment.
7. A declared language never overrides a detection. It is used only when
   nothing was detected.
8. `qwenbridge.language.configured` (default `en,fa,sv`) marks the languages a
   deployment is tuned for. It never restricts which languages are accepted
   or detected.

### Normalization: two separate representations

| Representation | Used for | Policy |
|---|---|---|
| `originalQuery` | Evidence, explanations, SSE | Never modified |
| `normalizedQuery` (Core `SearchTextNormalizer`) | AI analysis and retrieval | Remove disallowed controls (C0/C1 except tab/CR/LF; bidi embedding/override/isolate controls, LRM/RLM/ALM, BOM, soft hyphen); NFC; Persian yeh/kaf unification and tatweel removal **only for Persian**; ZWNJ tidying; Unicode whitespace collapse. No URL/HTML decoding, no NFKC, no case/digit/currency folding. Idempotent. |
| `threatCanonicalQuery` (existing rule chain) | Threat detection only | Unchanged aggressive chain: repeated URL decoding, HTML entities, NFKC, controls, whitespace. Never used as search or AI input. |

ZWNJ (Persian half-space), ZWJ and variation selectors (emoji sequences) and
emoji tag characters are preserved.

## Consequences

- Short or ambiguous queries now report `language: "unknown"` instead of
  `"en"`. Commerce translates only mixed text or text whose language was
  determined to be non-English, so it never translates on a guess.
- Responses gain an additive `languageAnalysis` object. The Java SDK now
  ignores unknown response fields. Before this change it failed on every real
  response because of the earlier `clarificationQuestions` field.
- AI analysis cache keys change for inputs whose NFKC and NFC forms differ, or
  that contain percent/HTML encodings. Stale entries are simply not hit.
- Detection accuracy on short text is limited by design. Measured quality per
  language needs a labelled dataset (QB-070, QB-207).
- Out of scope here: `CommerceExtraction.canonical` and `QueryTermReranker`
  still use NFKC to build comparison keys (Commerce and ranking phases,
  Sections 2–3).

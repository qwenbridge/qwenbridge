# Multilingual Typo Detection and Correction (QB-291)

QwenBridge can detect and correct typos in queries. The engine is local, stateless,
domain-neutral and needs no database, external AI API or SaaS. ADR 0054 records the decisions.

**No language is enabled or assumed by default.** The application owner configures the languages,
and a language is used only when a correction provider for it is installed.

## Configuration

**Application level** (`application.yaml` or environment variables):

```yaml
qwenbridge:
  typo:
    enabled: true                 # default false
    languages: [fa, de, en]       # default: none; any BCP 47 identifier
    mode: SAFE_AUTO_CORRECT       # DISABLED | SUGGEST_ONLY (default) | SAFE_AUTO_CORRECT | AGGRESSIVE
    safe-threshold: 0.75          # minimum confidence for SAFE_AUTO_CORRECT
    aggressive-threshold: 0.5     # minimum confidence for AGGRESSIVE
    dictionary-dir: /opt/qwenbridge/typo   # word lists for the built-in dictionary provider
```

Environment variables: `QWENBRIDGE_TYPO_ENABLED`, `QWENBRIDGE_TYPO_LANGUAGES=fa,de,en`,
`QWENBRIDGE_TYPO_MODE`, `QWENBRIDGE_TYPO_DICTIONARY_DIR`.

**Tenant level.** `qwenbridge.typo.tenants` is a JSON object of per-tenant settings. A tenant's
settings replace the application settings for that tenant only:

```json
{"shop-se": {"enabled": true, "languages": ["sv", "en"], "mode": "SAFE_AUTO_CORRECT"},
 "shop-ir": {"enabled": true, "languages": ["fa"], "mode": "SUGGEST_ONLY"}}
```

- **Isolation:** no tenant's languages affect another tenant.
- **Storage:** none needed; this is configuration.
- **Requests:** there is no request-level language setting, so a request cannot widen what its
  tenant allows.

**What each configuration does:**

| Configuration | Result |
|---|---|
| Not enabled (the default) | Status `DISABLED`; the query is untouched; responses carry no `spelling` field |
| Enabled, no languages | `UNCONFIGURED`; nothing is corrected; no language is assumed |
| Languages configured, no installed provider supports any of them | `NO_PROVIDER`; nothing is substituted |
| A language with a valid identifier but no provider | Reported `PROVIDER_UNAVAILABLE`; the other languages still work |
| An invalid identifier | Reported `INVALID_IDENTIFIER` |

`GET /api/v2/spelling` (permission `search`) shows the caller's effective settings, each
language's status, and the installed providers. Startup never fails because of typo configuration:
a broken dictionary directory is logged and skipped.

## Providers and dictionaries

Providers implement the Core SPI `io.qwenbridge.core.spelling.CorrectionProvider`. Each declares:
- its languages and scripts;
- its capabilities: `DICTIONARY`, `DOMAIN_VOCABULARY`, `PHRASES`, `FREQUENCIES`;
- its requirements;
- its entry count and approximate memory.

**Discovery:** providers come from `ServiceLoader` (installable modules or packs) and from the
built-in dictionary provider. The Core bundles no language data.

**The built-in dictionary provider** reads `dictionary-dir`:

| File | Content |
|---|---|
| `<bcp47>.txt` | One per language, for example `sv.txt`, `fa.txt`, `nb.txt`. One UTF-8 entry per line: `word` or `word<TAB>frequency`. A line with several words is a phrase: its words and adjacent pairs are registered, and the pairs are used for context. `#` starts a comment |
| `domain.txt` | Optional domain vocabulary (product, brand and model names) with canonical casing, such as `iPhone 16 Pro`. It applies to every configured language whose script it shares |

A file whose name is not a language tag is refused.

## Processing

1. **Settings:** the tenant's effective settings are read, and the languages are validated against
   BCP 47 and the providers.
2. **Normalization:** the query is normalized (NFC, whitespace) for searching. The original is
   always kept.
3. **Detection:** the language is detected.
   - **A reliably detected language that is not enabled:** the query is left alone
     (`LANGUAGE_NOT_ENABLED`). The exception is when an enabled language knows at least half of
     the query's plain words: typos mislead detectors ("blak shoes" reads as Afrikaans), and the
     explanation records the disagreement.
   - **A reliably detected language that is enabled:** if another enabled language knows at
     least half of the plain words and more of them than the detected one, that language is used,
     and the explanation says so. Example: "red wintr jaket under 100 EUR" reads as Swedish, but
     English knows more of its words (found by the QB-295 end-to-end suite).
   - **Mixed or undetermined text:** each word is checked against the enabled languages whose
     dictionaries share its script.
4. **Protected tokens:** these are never changed:
   - URLs, e-mail addresses, IP addresses, UUIDs and host names;
   - technical and product identifiers (`redis03-pt1`, `SKU-4411`);
   - numbers, currency amounts and quoted values;
   - acronyms (`EUR`);
   - words joined by `-`, `_` or `.`;
   - words shorter than three letters;
   - unknown mixed-case names.
5. **Candidates:** a word that any enabled language knows is never corrected, whatever language
   the detector chose. With `en` and `sv` enabled, the English "red" is not turned into the Swedish
   "röd" when a query is misdetected as Swedish (found during QB-294 runtime verification). For
   words no enabled dictionary knows, candidates are generated with:
   - weighted edit distance over Unicode code points (transpositions are cheaper);
   - keyboard proximity on the Latin QWERTY (with Nordic and German keys) and Persian layouts;
   - dictionary and domain vocabulary.
6. **Ranking and confidence:** candidates are ranked by distance, frequency, domain vocabulary and
   context (known adjacent pairs). Confidence falls when two candidates are close, so ties are
   suggested, never applied.
7. **The policy decides:**

   | Policy | Behaviour |
   |---|---|
   | `SUGGEST_ONLY` | Never changes the query |
   | `SAFE_AUTO_CORRECT` | Applies corrections with confidence ≥ `safe-threshold` and one edit (two for words of eight or more letters) |
   | `AGGRESSIVE` | Lower threshold, two edits |

8. **Output:** the query is rebuilt, with an explanation for every decision.

**v1 analyze:** the corrected search form feeds the model analysis and retrieval. Threat detection
always sees the original text.

**v2 search:** corrections are reported. The corrected text becomes the retrieval text when the
model gives no rewrite of its own.

**Hard constraints:** in both versions, they are always extracted from and grounded in the
original text.

## QueryIR 1.1

QueryIR documents get an optional `correction` object once typo correction is enabled:
- `status`, `mode`;
- `normalizedText`, `correctedText`;
- `configuredLanguages`, `activeLanguages`, `detectedLanguage`;
- `corrections`, each with `token`, `start`, `end`, `language`, `replacement`, `confidence`,
  `applied` and `reason`.

**Compatibility:**
- **Unchanged documents:** documents without it stay `1.0`, byte for byte.
- **Readers:** they accept 1.0 and 1.1; in a 1.0 document `correction` is an unknown field.
- **Original text:** `originalText` is never the corrected text.

## API and SDKs

**Responses.** v1 `POST /api/v1/search/analyze`, v2 search responses and their SSE terminal events
carry `spelling` only when typo correction is enabled for the caller:
- `status`, `mode`;
- `original`, `normalized`, `corrected`;
- `languages` (the status of each);
- `detectedLanguage`, `activeLanguages`;
- `corrections`, each with its candidates and scores, replacement, confidence, applied and reason;
- `protectedTokens`, `explanations`.

**SDKs:**
- **Java:** `io.qwenbridge.sdk.spelling.Spelling`, on `SearchAnalyzeResponse.spelling()` and
  `CommerceContracts.Response.spelling()`.
- **TypeScript:** the `Spelling` type, on `SearchAnalyzeResponse.spelling` and
  `CommerceResponse.spelling`.
- **Compatibility:** older SDKs ignore the field.

## Accuracy, false positives and performance

**Accuracy:** `TypoAccuracyReportTest` ran a labelled set in `SAFE_AUTO_CORRECT` with fixture
dictionaries (English, Swedish, Persian, domain). It writes `target/benchmarks/typo-accuracy.json`.

| Set | Size | Result |
|---|---|---|
| Typos | 17 | 17 fixed, 0 wrong, 0 missed (precision 1.0, recall 1.0) |
| Clean queries | 11 | 0 false positives |
| Protected inputs | 10 | 0 changed |

The fixture dictionaries were written alongside the cases, so these figures show the engine's
decisions, not real-world accuracy. Production accuracy depends on the dictionaries an application
installs, and must be measured with them.

**Performance:** measured by `TypoCorrectorTest.latencyWithALargeDictionary` on this laptop. A
synthetic 50,000-word dictionary gives p50 7.8 ms and p95 10.9 ms per query, at about 6 MB of heap.
Candidate generation scans the words of similar length, so cost grows linearly with dictionary
size. A symmetric-delete index would be the next step for dictionaries of a million or more words.

## Limitations

- **Dictionary quality:** the engine is only as good as the installed dictionaries. None ship with
  the Core.
- **Keyboard layouts:** proximity is modelled for Latin QWERTY (with Nordic and German keys) and
  Persian only; other scripts use plain edit distance.
- **Context:** only adjacent word pairs from phrase lines are used; there is no language model.
- **Casing:** canonical casing comes from the vocabulary (`iPhone`). Other words follow the input's
  capitalization; phrase-wide re-casing is not applied.

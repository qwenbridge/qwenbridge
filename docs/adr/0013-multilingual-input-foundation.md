# ADR 0013: Multilingual Input Foundation

## Status

Accepted

## Context

QwenBridge is designed to accept user input independently of the language,
script, locale, or client used to submit the request.

The original query must remain available exactly as it was received while
later pipeline phases perform normalization, language detection, translation,
semantic analysis, threat detection, and search planning.

Language-related concepts also have different meanings and must not be
conflated:

- Original text is the exact user-provided input.
- Declared language is optional metadata supplied by a client.
- Locale is optional regional and language metadata.
- Detected language is derived independently by language analysis.
- Input source identifies the trusted ingestion boundary.

Without an explicit input contract, later phases could accidentally make the
pipeline English-first, overwrite the original query, treat client-declared
language as detected language, or couple the input model to AI or provider
implementations.

## Decision

QwenBridge uses `MultilingualInput` as the canonical multilingual input
contract at the pipeline boundary.

The contract contains:

- `originalText`
- `declaredLanguage`
- `locale`
- `source`

`originalText` is lossless input and must not be normalized, translated,
trimmed, rewritten, or otherwise modified during ingestion.

`declaredLanguage` is optional client metadata. It is not treated as detected
language and does not override language detection.

`locale` is optional metadata and remains independent from both declared and
detected language.

`source` represents the trusted ingestion boundary. For the HTTP search API,
the server assigns `InputSource.API`; clients do not control this value.

The existing string-based request and execution-context APIs remain supported
for backward compatibility and are adapted to `MultilingualInput`.

Language detection remains a separate pipeline responsibility and produces
`LanguageResult`. It does not modify `MultilingualInput`.

Normalization, translation, mixed-language understanding, and other derived
representations must be stored separately from the original input.

## Validation

The public HTTP API validates syntax for optional language and locale metadata
before constructing `MultilingualInput`.

The current API contract accepts:

- A two-letter ISO 639-1-style declared language code
- A supported BCP 47-style locale language tag

Validation of metadata syntax does not constitute language detection.

## Architectural Boundaries

The multilingual input model must remain independent from:

- API controllers
- Language detection implementations
- AI implementations
- Provider implementations
- Search backend implementations

Pipeline and ingestion layers may depend on the multilingual input model.

The input model must not depend on those higher-level layers.

## Consequences

Positive:

- QwenBridge remains multilingual-first rather than English-first.
- Original user input remains available for auditing and later analysis.
- Declared and detected language have explicit, separate semantics.
- Locale metadata can evolve independently.
- New ingestion channels can identify their source without changing the core
  input contract.
- Later normalization and translation phases can create derived
  representations without destroying the original input.
- Existing clients remain backward compatible.

Trade-offs:

- Multiple representations of the same request may exist later in the
  pipeline.
- Ingestion adapters are responsible for constructing the canonical input
  contract correctly.
- Language and locale metadata require explicit validation at public
  boundaries.

## Future Work

This ADR establishes only the multilingual input foundation.

Language detection and normalization are handled by Phase 2.

Translation and mixed-language understanding are handled by Phase 3.

Those phases may introduce derived representations, but they must preserve the
original `MultilingualInput`.

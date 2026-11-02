# ADR 0055: Universal Configurable Multilingual Query Expansion

## Status

Accepted (QB-286).

## Decision

1. **Core engine.** `io.qwenbridge.core.expansion.QueryExpander` is stateless and contains no
   language list. One query gives one result per configured, non-duplicate target, in configured
   order, with explicit statuses. No text is invented for a failed target.
2. **Explicit configuration.** Expansion is off by default, with no targets and no providers. A
   tenant JSON overrides the application settings; no storage is involved.
   - **Identifiers:** validated against BCP 47 and the JDK ISO 639 and ISO 3166 lists.
   - **Upper-case region codes are not guessed:** `NO` is ambiguous, and `DK` is invalid.
   - **Aliases are explicit and matched exactly.** `SE=sv` is the documented default; `se` stays
     Northern Sami.
3. **Policies.** `ALL_TARGETS` (default), `STRICT_ALL_TARGETS` and `BEST_EFFORT_ALL_TARGETS` differ
   only in what retrieval uses and in the overall status. Every target is always attempted and
   reported.
4. **Provider SPI.** `TranslationProvider` is extended with defaults: declared pairs,
   availability, requirements, metadata and vocabulary evidence. Selection is by capability. A
   provider that cannot start stays as unavailable, so its targets say why.
   - **Two local providers:** phrase tables (`glossary`) and the configured local chat model
     (`model`).
5. **Preservation.** It reuses `TranslationStage` with `ENFORCE`, as in QB-018, and adds a verbatim
   check of protected identifiers (QB-291's protected-token patterns).
6. **Source language.** It is independent of the targets. Script-based detection, mixed text and
   declared languages are kept.
   - **Vocabulary evidence:** a statistical detection without a score, or no detection, may be
     overruled by provider vocabulary covering at least half of the words. The decision is
     explained. This was measured as necessary: the configured detector labels "I need a red winter
     jacket under 100 euros" as Swedish.
7. **Bounds.**
   - **Concurrency:** per-request parallelism, plus one process-wide semaphore.
   - **Time:** per-language and overall timeouts, both bounded by the request deadline.
   - **Cancellation:** stops waiting and interrupts the workers.
8. **Safety order.** Expansion runs only after both safety checks allow the query: order 28 in v1,
   after the model safety decision in v2. A held or blocked query never reaches a provider.
9. **Contracts.**
   - **QueryIR 1.1:** gains the optional `expansion`. 1.1 is not yet released, so the minor version
     stays the same; 1.0 documents are unchanged.
   - **API:** `expansion` on v1 and v2 responses and the SSE terminal event, plus
     `GET /api/v2/multilingual`.
   - **SDKs:** both are typed for it, against a golden fixture captured from the server.

## Consequences

- **Default deployments are unchanged:** one disabled check, and no field.
- **Quality depends on the installed providers.** The fixtures prove mechanics, not translation
  quality. Model translations are preservation-checked but not scored.
- **Retrieval is not switched over.** Commerce retrieval does not yet use the variants (documented
  limitation). The Core helper `ExpansionRetrieval` is ready for it.
- **Source detection remains an upstream limitation** where no provider has a vocabulary.

# ADR 0057: Dual-SDK End-to-End Suite

## Status

Accepted (QB-295).

## Decision

1. **Two independent consumers, one server.** A standalone Java 25 Maven project uses the SDK
   artifact from the Maven repository. A standalone TypeScript package uses the SDK's packed
   tarball, imported by package name. Both run against the same real, packaged Quarkus server.
   Neither uses server classes or SDK source files.
2. **One scenario contract and one evaluator.**
   - **Consumers** execute and emit normalized observations; they judge nothing.
   - **The evaluator** applies the same expectations to both and compares their contract views.
     A difference is a failure (`DIVERGENT`).
   - **Expectations** are invariants (constraints, statuses, id sets, grounded evidence,
     per-language results), never byte-for-byte responses.
3. **A controlled, deterministic environment.** Nothing inside the SDKs or the server is mocked.
   - **Catalog:** `qwenbridge.demo.dataset-files` (new, off by default, memory backend only) loads
     a deterministic catalog. `qwenbridge.demo.embed` (new, off by default) embeds it as catalog
     sync does.
   - **Model:** a local Ollama-compatible stand-in serves reviewed analyses, translations and
     deterministic lexical embeddings. Unknown prompts get 503, so nothing is invented.
4. **Three modes.**
   - **`OFFLINE_BASELINE`** is mandatory, with no external service.
   - **`LOCAL_PROVIDER_INTEGRATION`** is real catalog synchronization from a local
     provider-neutral feed with switchable failure modes. It needs Redis, which catalog sync
     itself requires.
   - **`AUTHORIZED_LIVE_PROVIDER`** runs live checks only with explicit authorization and
     credentials. Otherwise they are BLOCKED; mock results are never reported as live.
5. **Integration.** Master step 24 runs all modes. CI runs the offline baseline on every push and
   PR. Live checks have a separate, manually dispatched, explicitly gated CI job.

## Consequences

- **What the suite proves.** It verifies contracts, grounding, security, streaming and resilience
  across both SDKs on every master run. It does not measure model or semantic-ranking quality;
  steps 21 and 22 use the real local model.
- **QB-291 fix found by the suite.** The typo engine's vocabulary-evidence rule now also applies
  when the detected language is enabled but another enabled language knows more of the query's
  words.

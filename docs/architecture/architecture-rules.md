# Architecture Rules

Architecture rules are enforced by ArchUnit tests and code review.

- API packages must not expose provider DTOs as public contracts.
- Provider implementations must depend on provider-neutral SPI contracts.
- Pipeline steps communicate through typed execution-context keys and result values.
- Execution operations are handled by dedicated executors.
- Streaming delivery must not become a dependency of core pipeline execution.
- Threat detection must remain modular and independently testable.
- New architectural decisions require documentation and, where durable, an ADR.

## Multilingual Input

- `MultilingualInput` is the canonical multilingual input contract at the pipeline boundary.
- Original user input must remain lossless and must not be replaced by normalized, translated, or rewritten text.
- Declared language metadata must remain separate from detected language.
- Locale metadata must remain independent from language detection.
- Input source must be assigned by the trusted ingestion boundary rather than trusted from unverified client input.
- The input model must remain independent from API, language-detection, AI, provider, and search-backend implementations.
- Normalization, translation, and other derived representations must be modeled separately from the original input.

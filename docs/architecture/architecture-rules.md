# Architecture Rules

Architecture rules are enforced by ArchUnit tests and code review.

- API packages must not expose provider DTOs as public contracts.
- Provider implementations must depend on provider-neutral SPI contracts.
- Pipeline steps communicate through typed execution-context keys and result values.
- Execution operations are handled by dedicated executors.
- Streaming delivery must not become a dependency of core pipeline execution.
- Threat detection must remain modular and independently testable.
- New architectural decisions require documentation and, where durable, an ADR.

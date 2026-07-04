# V8 Architecture Enforcement

QwenBridge V8 protects module boundaries with ArchUnit tests.

## Enforced rules

- API/controller packages must not depend directly on concrete provider implementations.
- Provider SPI packages must not depend on API/web packages.
- API, pipeline, domain service, and provider layers are validated by the architecture test suite.

## Release rule

A release is not valid unless the `architecture-test` CI job passes.

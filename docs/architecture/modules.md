# Modules

## Repository modules

| Module | Responsibility |
|---|---|
| `qwenbridge-core` | Framework-free engine; depends only on Jackson |
| `qwenbridge-plugin-sdk` | Public plugin and pack contracts; JDK only |
| `qwenbridge-commerce-pack` | Commerce domain pack |
| `qwenbridge-library-pack` | Library domain pack, a second, independent domain |
| `qwenbridge-server` | Quarkus server: REST and SSE APIs, pipeline, providers, platform, operations |
| `qwenbridge-java-sdk` | Java 25 client |
| `qwenbridge-typescript-sdk` | TypeScript client (npm `@qwenbridge/sdk`) |
| `e2e/dual-sdk` | End-to-end suite with independent Java and TypeScript consumers |
| `evaluation` | Datasets, baselines and benchmarks |
| `docs` | Architecture, API, operations, release and contributor documentation |
| `scripts` | Verification, seed and performance tooling |

QB-287 removed the four connector modules (Elasticsearch, PostgreSQL, MongoDB, Shopify) and
`examples/` (ADR 0058).

## Server package boundaries

The server is organized by capability. Provider-specific code stays under provider packages, and
public contracts are isolated from provider DTOs. The package map is in the
[overview](overview.md#boundaries).

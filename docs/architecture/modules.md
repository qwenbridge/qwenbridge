# Modules

## Repository modules

| Module | Responsibility |
| --- | --- |
| `qwenbridge-server` | Spring Boot server, pipeline, providers, APIs, SSE, operations |
| `qwenbridge-java-sdk` | Java API and typed streaming client |
| `qwenbridge-spring-boot-starter` | Spring Boot auto-configuration for the Java SDK |
| `qwenbridge-typescript-sdk` | TypeScript API and typed streaming client |
| `examples` | Runnable consumer examples |
| `docs` | Architecture, API, operations, release, and contributor documentation |
| `scripts` | Release verification, seed, and performance tooling |

## Server package boundaries

The server is organized by business capability rather than framework layer alone. Provider-specific code remains under provider packages; public contracts remain isolated from provider DTOs.

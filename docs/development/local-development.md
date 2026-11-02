# Local Development

## Prerequisites

- Java 25
- Maven 3.9+
- Docker and Docker Compose
- Node.js 22.12+, 24 or 26 for the TypeScript development/test tools
- Ollama with the configured Qwen and embedding models

## Start dependencies

```bash
docker compose up -d
```

Verify the local dependencies:

```bash
docker compose ps
curl -fsS http://localhost:11434/api/tags
# only with the optional OpenSearch profile (docker compose --profile opensearch up -d):
# curl -fsS http://localhost:9200
```

## Run the server

Activate JDK 25 in the terminal before direct `mvn`/`java` commands. The verification
entry points select JDK 25 automatically; a system-wide Java 21 default is not sufficient.

Live-coding development mode (hot reload):

```bash
mvn -pl qwenbridge-server quarkus:dev
```

Or run the packaged application:

```bash
mvn -pl qwenbridge-server package
java -jar qwenbridge-server/target/quarkus-app/quarkus-run.jar
```

The local API is available at `http://localhost:8080`.

## Seed OpenSearch (optional backend only)

The default backend is in-process and needs no seeding. Only when you select OpenSearch
(`QWENBRIDGE_SEARCH_BACKEND=opensearch`, `QWENBRIDGE_COMMERCE_BACKEND=opensearch`):

```bash
docker compose --profile opensearch up -d
bash scripts/opensearch-seed.sh
```

## Useful local checks

```bash
mvn clean verify
bash scripts/verify-release.sh
```

For the complete test flow, including Docker preparation, seeding, live JUnit
integrations, TypeScript, static analysis, release checks and all k6 workloads:

```bash
bash scripts/qwenbridge-final-verification.sh
```

This command includes `mvn clean install`. Its default load is one million total
requests with 250 VUs; see [testing](testing.md) for smaller runs.

## Project layout

| Path | Purpose |
| --- | --- |
| `qwenbridge-core/` | Framework-free engine (Jackson only) |
| `qwenbridge-plugin-sdk/`, `qwenbridge-commerce-pack/`, `qwenbridge-library-pack/` | Plugin contracts and the two domain packs |
| `qwenbridge-server/` | Quarkus Java 25 application, resources and JUnit tests |
| `qwenbridge-java-sdk/` | Java 25 SDK and its tests |
| `qwenbridge-typescript-sdk/` | TypeScript SDK and Vitest tests |
| `e2e/dual-sdk/` | Independent Java and TypeScript SDK consumers and the end-to-end suite (replaces the former `examples/`, QB-287) |
| `scripts/` | Executable verification entry points and seed script |
| `scripts/verification/` | Shared helpers sourced by the entry points |
| `scripts/performance/` | k6 workloads, executed by k6 rather than Bash |
| `docs/` | Architecture, operation and acceptance evidence |
| `logs/verification/` | Ignored test reports; `archive/` holds historical verification bundles |

The root POM aggregates the server, Java SDK and Java example. The npm packages
have their own builds and are included by the verification entry points.
`target/`, `dist/`, `node_modules/` and Python caches are generated/ignored files.
The former `mnt/` contained only two redundant validator snapshots and was removed.
`RedisConcurrentAccessIntegrationTest` belongs under the server's `src/test/java`;
the complete entry point enables its live Redis flag after dependencies are ready.

## Configuration

Use environment variables or a local, uncommitted configuration override for credentials and deployment-specific values. Do not commit secrets, tokens, passwords, or private endpoints.

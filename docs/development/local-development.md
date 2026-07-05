# Local Development

## Prerequisites

- Java 21
- Maven 3.9+
- Docker and Docker Compose
- Node.js 20+ for the TypeScript SDK and example
- Ollama with the configured Qwen and embedding models

## Start dependencies

```bash
docker compose up -d
```

Verify the local dependencies:

```bash
docker compose ps
curl -fsS http://localhost:9200
curl -fsS http://localhost:11434/api/tags
```

## Run the server

```bash
mvn -pl qwenbridge-server spring-boot:run
```

The local API is available at `http://localhost:8080`.

## Seed OpenSearch

```bash
bash scripts/opensearch-seed.sh
```

## Useful local checks

```bash
mvn clean verify
bash scripts/verify-release.sh
```

## Configuration

Use environment variables or a local, uncommitted configuration override for credentials and deployment-specific values. Do not commit secrets, tokens, passwords, or private endpoints.

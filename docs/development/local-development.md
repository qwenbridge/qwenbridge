# Local Development

## Prerequisites

- Java 25
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

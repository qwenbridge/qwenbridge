# Docker Deployment

QwenBridge ships a Dockerfile for the server and a Compose file for local development and
release verification.

## Build the server image

```bash
docker build -f qwenbridge-server/Dockerfile -t qwenbridge-server:local .
```

The build context is the repository root, because the image builds the whole reactor. The image
runs `java -jar /app/quarkus-run.jar` on Java 25.

## Compose profiles

| Command | Starts |
|---|---|
| `docker compose up -d` | Ollama (with the model pull) and Redis. No database, no search engine |
| `docker compose --profile production up -d --build` | The application as well. It is database-free: in-process retrieval, file-backed state on the `qwenbridge-state` volume |
| `... --profile opensearch` | Optional OpenSearch. Select it with `QWENBRIDGE_COMMERCE_BACKEND=opensearch QWENBRIDGE_SEARCH_BACKEND=opensearch` |
| `... --profile performance` | k6 load generator |

The application does not wait for Redis or OpenSearch (`required: false`). Ports are published on
`127.0.0.1` only. The local OpenSearch has security disabled and is for development only. The
secured variant is `docker-compose.security-tests.yml`.

## Verify

```bash
docker compose ps
curl -fs http://localhost:8080/api/v1/health/ready
bash scripts/verify-release.sh
```

## Production notes

For production, configure:
- explicit environment variables and API principals (the `prod` profile requires them);
- externalized secrets and TLS;
- readiness and liveness probes (`/q/health/ready`, `/q/health/live`);
- log aggregation and metrics scraping (`/q/metrics`);
- resource limits and dependency timeouts.

Never use local development credentials in production. Never run `docker compose down -v` against
a stack whose volumes hold state you need.

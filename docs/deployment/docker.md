# Docker Deployment

QwenBridge includes Docker support for local development and deployment verification.

## Build server image

```bash
mvn -pl qwenbridge-server clean package
docker build -t qwenbridge-server:local qwenbridge-server
```

## Start local dependencies

```bash
docker compose up -d
```

The compose stack is intended for local development and release verification.

## Verify containers

```bash
docker compose ps
```

Check the expected dependencies:

- OpenSearch
- Redis
- Ollama, when configured through the local stack

## Run release verification

```bash
bash scripts/verify-release.sh
```

## Production notes

For production, configure:

- explicit environment variables
- externalized secrets
- readiness and liveness probes
- log aggregation
- metrics scraping
- resource limits
- dependency timeouts
- TLS and network controls

Do not use local development credentials or local-only configuration in production.
